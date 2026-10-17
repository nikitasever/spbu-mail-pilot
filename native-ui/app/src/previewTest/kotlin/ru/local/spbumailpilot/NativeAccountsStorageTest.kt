package ru.local.spbumailpilot

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Serialization contracts only; Android Keystore and live servers require device acceptance. */
class NativeAccountsStorageTest {
    private fun login(user:String="one")=SavedAccounts.Login("mail.spbu.ru",993,user,"fixture-password")
    @Test fun inactiveAccountsRemainInactiveAfterRoundTrip(){
        val saved=SavedAccounts();saved.save(login());saved.save(login("two"));saved.deactivate()
        val restored=CredentialVault.decode(CredentialVault.encode(saved))
        assertNull(restored.active());assertEquals(2,restored.list().size)
    }
    @Test fun smtpUpdatePreservesOtherSavedAccountsAndOwnerIdentity(){
        val saved=SavedAccounts();val first=login();val second=login("two");saved.save(first);saved.save(second)
        val updated=NativeAccountPolicy.smtp(first,"smtp.example.org","587",false,"alias@example.org");saved.save(updated)
        val restored=CredentialVault.decode(CredentialVault.encode(saved))
        assertEquals(2,restored.list().size);assertEquals(first.identity(),restored.active().identity())
        assertTrue(NativeAccountPolicy.same(updated,restored.active()))
        assertTrue(NativeAccountPolicy.same(second,restored.list().single {it.user=="two"}))
    }
    @Test fun oldSingleLoginSchemaStillLoads(){
        val old=JSONObject().put("host","mail.spbu.ru").put("port",993).put("user","one").put("password","fixture-password")
        val restored=CredentialVault.decode(old.toString());assertEquals(1,restored.list().size);assertEquals("one",restored.active().user)
    }
    @Test fun removingActiveDoesNotSelectAnotherAccount(){
        val saved=SavedAccounts();saved.save(login());val second=login("two");saved.save(second);saved.remove(second)
        val restored=CredentialVault.decode(CredentialVault.encode(saved));assertNull(restored.active());assertEquals(1,restored.list().size)
    }
    @Test fun emptyVaultRoundTripDoesNotRequireImplicitSelection(){
        val restored=CredentialVault.decode(CredentialVault.encode(SavedAccounts()));assertNull(restored.active());assertTrue(restored.list().isEmpty())
    }
    @Test fun uiDiagnosticStringCannotRevealTypedPassword(){
        val ui=AccountsState(user="private-user",password="private-secret",host="private-host")
        assertFalse(ui.toString().contains("private-"))
    }
    @Test fun unknownSchemaIsRejected(){
        assertThrows(Exception::class.java){CredentialVault.decode("{\"schema\":3,\"accounts\":[],\"active\":\"\"}")}
    }
    @Test fun passwordUpdateOnInactivePreservesActiveAndCustomSmtp(){
        val saved=SavedAccounts()
        val activeA=login("active-a")
        val inactiveB=NativeAccountPolicy.smtp(login("inactive-b"),"smtp.custom.org","587",false,"b@custom.org")
        saved.save(inactiveB);saved.save(activeA)
        val targetB=NativeAccountPolicy.password(inactiveB,"updated-secret-b")
        saved.updatePassword(inactiveB,targetB.password)
        val restored=CredentialVault.decode(CredentialVault.encode(saved))
        assertEquals(activeA.identity(),restored.activeId())
        assertTrue(NativeAccountPolicy.same(activeA,restored.active()))
        assertTrue(NativeAccountPolicy.same(targetB,restored.get(inactiveB.identity())))
    }
}
