"""Preserve the bundled Java launcher's manifest, enabling UTF-8 paths on Windows 10/11."""
import ctypes
from ctypes import wintypes as w
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def enable_utf8(executable: Path):
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    language_callback = ctypes.WINFUNCTYPE(w.BOOL, w.HMODULE, w.LPCWSTR, w.LPCWSTR, w.WORD, ctypes.c_ssize_t)
    signatures = {
        'LoadLibraryExW': ([w.LPCWSTR, w.HANDLE, w.DWORD], w.HMODULE),
        'FindResourceW': ([w.HMODULE, w.LPCWSTR, w.LPCWSTR], w.HANDLE),
        'SizeofResource': ([w.HMODULE, w.HANDLE], w.DWORD),
        'LoadResource': ([w.HMODULE, w.HANDLE], w.HANDLE),
        'LockResource': ([w.HANDLE], ctypes.c_void_p),
        'FreeLibrary': ([w.HMODULE], w.BOOL),
        'EnumResourceLanguagesW': ([w.HMODULE, w.LPCWSTR, w.LPCWSTR, language_callback, ctypes.c_ssize_t], w.BOOL),
        'BeginUpdateResourceW': ([w.LPCWSTR, w.BOOL], w.HANDLE),
        'UpdateResourceW': ([w.HANDLE, w.LPCWSTR, w.LPCWSTR, w.WORD, ctypes.c_void_p, w.DWORD], w.BOOL),
        'EndUpdateResourceW': ([w.HANDLE, w.BOOL], w.BOOL),
    }
    for name, (args, result) in signatures.items():
        function = getattr(kernel, name)
        function.argtypes, function.restype = args, result
    resource_id, resource_type = ctypes.cast(1, w.LPCWSTR), ctypes.cast(24, w.LPCWSTR)
    module = kernel.LoadLibraryExW(str(executable), None, 2)
    if not module: raise ctypes.WinError(ctypes.get_last_error())
    languages = []
    @language_callback
    def collect_language(module, kind, name, language, parameter):
        languages.append(language)
        return True
    try:
        if not kernel.EnumResourceLanguagesW(module, resource_type, resource_id, collect_language, 0):
            raise ctypes.WinError(ctypes.get_last_error())
        resource = kernel.FindResourceW(module, resource_id, resource_type)
        if not resource: raise ctypes.WinError(ctypes.get_last_error())
        size = kernel.SizeofResource(module, resource)
        data = kernel.LockResource(kernel.LoadResource(module, resource))
        if not data or not size: raise RuntimeError('Java launcher manifest missing')
        manifest = ET.fromstring(ctypes.string_at(data, size))
    finally:
        kernel.FreeLibrary(module)
    assembly = 'urn:schemas-microsoft-com:asm.v3'
    settings = 'http://schemas.microsoft.com/SMI/2019/WindowsSettings'
    app = manifest.find('{' + assembly + '}application')
    if app is None: app = ET.SubElement(manifest, '{' + assembly + '}application')
    windows = app.find('{' + assembly + '}windowsSettings')
    if windows is None: windows = ET.SubElement(app, '{' + assembly + '}windowsSettings')
    codepage = windows.find('{' + settings + '}activeCodePage')
    if codepage is None: codepage = ET.SubElement(windows, '{' + settings + '}activeCodePage')
    codepage.text = 'UTF-8'
    payload = ET.tostring(manifest, encoding='utf-8', xml_declaration=True)
    handle = kernel.BeginUpdateResourceW(str(executable), False)
    if not handle: raise ctypes.WinError(ctypes.get_last_error())
    buffer = ctypes.create_string_buffer(payload)
    for language in languages:
        if not kernel.UpdateResourceW(handle, resource_type, resource_id, language, buffer, len(payload)):
            error = ctypes.get_last_error()
            kernel.EndUpdateResourceW(handle, True)
            raise ctypes.WinError(error)
    if not kernel.EndUpdateResourceW(handle, False): raise ctypes.WinError(ctypes.get_last_error())


if __name__ == '__main__':
    enable_utf8(Path(sys.argv[1]).resolve())
