package ru.local.spbumailpilot;
/** Picker identity survives pause, but not a different account or a reopened import form. */
final class NativeContactFilePick {
    final String owner;final long session;
    NativeContactFilePick(String owner,long session){if(!owner.matches("account-[0-9a-f]{64}")||session<=0)throw new IllegalArgumentException("Contact picker identity");this.owner=owner;this.session=session;}
    boolean matches(String owner,long session){return this.owner.equals(owner)&&this.session==session;}
    public String toString(){return "NativeContactFilePick(pending)";}
}
