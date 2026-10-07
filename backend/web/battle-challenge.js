"use strict";
let port=null, pending=null, sent=false;
function sendToken(){if(port && pending && !sent){sent=true;port.postMessage(pending);pending=null;port.close();}}
window.addEventListener("message",event=>{
  // Android's native postWebMessage has no source window or web origin.
  // Frame messages have a source window and cannot claim this channel.
  if(event.source!==null || !["", "null"].includes(event.origin) || event.data!=="doomscore-connect" || event.ports.length!==1 || port)return;
  port=event.ports[0];sendToken();
});
window.doomscoreChallengeReady=()=>{
  const container=document.getElementById("challenge");
  if(container.dataset.sitekey.startsWith("REPLACE_")){document.getElementById("status").textContent="Battle verification is awaiting configuration.";return;}
  turnstile.render(container,{sitekey:container.dataset.sitekey,action:"battle-signup",
    callback:token=>{pending=token;sendToken();},
    "expired-callback":()=>{pending=null;},
    "error-callback":()=>{pending=null;document.getElementById("status").textContent="Please close this check and try again.";}
  });
};
