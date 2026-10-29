const dialog=()=>document.getElementById('confirm-dialog') as HTMLDialogElement;
let resolve:((value:boolean)=>void)|null=null;let trigger:HTMLElement|null=null;
export function confirmationOpen(){return Boolean(resolve)}
export function confirmAction(title:string,message:string,action:string):Promise<boolean>{
 if(resolve)return Promise.resolve(false);trigger=document.activeElement instanceof HTMLElement?document.activeElement:null;
 document.getElementById('confirm-title')!.textContent=title;document.getElementById('confirm-message')!.textContent=message;document.getElementById('confirm-accept')!.textContent=action;
 return new Promise(done=>{resolve=done;dialog().showModal();document.getElementById('confirm-keep')!.focus()});
}
export function cancelConfirmation(){finish(false)}
function finish(value:boolean){if(!resolve)return;const done=resolve;resolve=null;dialog().close();const focus=trigger;trigger=null;if(focus?.isConnected)focus.focus();done(value)}
export function setupConfirmation(){document.getElementById('confirm-keep')!.addEventListener('click',()=>finish(false));document.getElementById('confirm-accept')!.addEventListener('click',()=>finish(true));dialog().addEventListener('cancel',event=>{event.preventDefault();finish(false)})}
