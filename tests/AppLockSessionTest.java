package ru.local.spbumailpilot;
public final class AppLockSessionTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  Object main=new Object(),pdf=new Object(),rotated=new Object();AppLockSession state=new AppLockSession(false);check(state.allowed()&&!state.enabled());state.start(main);
  long first=state.begin(main,false);check(!state.accept(pdf,first));check(state.accept(main,first));check(!state.accept(main,first));state.enabled(true);check(state.enabled()&&state.allowed());
  state.start(pdf);state.stop(main,false);check(state.allowed());state.stop(pdf,true);state.start(rotated);check(state.allowed());state.stop(rotated,false);check(!state.allowed());
  state.start(main);long late=state.begin(main,false);state.stop(main,false);state.start(main);check(!state.accept(main,late)&&!state.allowed());
  long cancelled=state.begin(main,false);state.cancel(main);check(!state.accept(main,cancelled));
  long external=state.begin(main,true);state.stop(main,false);check(!state.accept(main,external));state.start(main);check(state.accept(main,external)&&state.allowed());
  state.stop(main,false);state.start(main);long obsolete=state.begin(main,true);state.destroy(main,false);state.start(rotated);check(!state.accept(main,obsolete)&&!state.allowed());
  long superseded=state.begin(rotated,false),current=state.begin(rotated,false);check(!state.accept(rotated,superseded));check(state.accept(rotated,current)&&state.allowed());state.destroy(rotated,false);check(!state.allowed());
  state.start(main);long revoked=state.begin(main,false);state.enabled(false);check(state.allowed()&&!state.accept(main,revoked));
  AppLockSession restart=new AppLockSession(true);restart.start(main);check(!restart.allowed());check(!restart.accept(main,external));
  check(AppLockSession.credentialCode(10000)==10000&&AppLockSession.credentialCode(10001)==10001);check(AppLockSession.credentialCode(Integer.MAX_VALUE)==Integer.MAX_VALUE);check(AppLockSession.credentialCode((long)Integer.MAX_VALUE+1)==-1&&AppLockSession.credentialCode(9999)==-1);
  System.out.println("PASS: app-lock opt-in/cold-start, global internal-activity/rotation continuity, background relock, one-use/owner/foreground challenge guards, stale/cancelled/superseded results, trusted external-credential roundtrip and destroy invalidation.");
 }
}
