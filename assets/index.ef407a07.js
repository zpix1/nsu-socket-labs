var _=Object.defineProperty;var j=Object.getOwnPropertySymbols;var D=Object.prototype.hasOwnProperty,C=Object.prototype.propertyIsEnumerable;var k=(i,e,r)=>e in i?_(i,e,{enumerable:!0,configurable:!0,writable:!0,value:r}):i[e]=r,$=(i,e)=>{for(var r in e||(e={}))D.call(e,r)&&k(i,r,e[r]);if(j)for(var r of j(e))C.call(e,r)&&k(i,r,e[r]);return i};import{r as d,B as b,d as W,a as B,s as u,f,b as F,m as p,c as L,t as O,e as v,g as w,o as A,h as y,i as m,j as K,k as q,l as N,R as H,n as Y}from"./vendor.5b96542d.js";const G=function(){const e=document.createElement("link").relList;if(e&&e.supports&&e.supports("modulepreload"))return;for(const t of document.querySelectorAll('link[rel="modulepreload"]'))n(t);new MutationObserver(t=>{for(const s of t)if(s.type==="childList")for(const o of s.addedNodes)o.tagName==="LINK"&&o.rel==="modulepreload"&&n(o)}).observe(document,{childList:!0,subtree:!0});function r(t){const s={};return t.integrity&&(s.integrity=t.integrity),t.referrerpolicy&&(s.referrerPolicy=t.referrerpolicy),t.crossorigin==="use-credentials"?s.credentials="include":t.crossorigin==="anonymous"?s.credentials="omit":s.credentials="same-origin",s}function n(t){if(t.ep)return;t.ep=!0;const s=r(t);fetch(t.href,s)}};G();const Q="0875266e-e97d-4271-8c05-c0429e6e3355",R="5ae2e3f221c38a28845f05b636b6fd4de4c4cc70484a64f28bd851e2",U="af633a14d22f0bd0ef1aa7dbbf25c580",z=(i,e)=>{d.exports.useEffect(()=>{const r=i.subscribe(n=>e(n));return()=>r.unsubscribe()},[])},E=(i,e,r)=>{const[n,t]=d.exports.useState(null),[s,o]=d.exports.useState(!1),[l,g]=d.exports.useState(null);return d.exports.useEffect(()=>{e.next(i)},[i]),z(r,({data:S,status:x,error:P})=>{x==="ok"&&S?(t(S),g(null),o(!1)):x==="loading"?(o(!0),g(null)):x==="error"&&P&&(g(P),o(!1))}),[n,s,l]},I=new b(""),V=I.pipe(W(200),B(),u(i=>i.length>1?f(`https://graphhopper.com/api/1/geocode?q=${i}&locale=ru&debug=true&key=${Q}&limit=100`).pipe(u(e=>{if(e.ok)return e.json();throw new Error(e.statusText)}),u(e=>F(e.hits).pipe(p(({country:r,city:n,name:t,point:s,osm_id:o})=>$({title:[r,n,t].filter(l=>!!l).join(", "),id:o.toString()},s)),L(r=>r.title),O(),p(r=>({status:"ok",data:r})))),v({status:"loading"}),w(async e=>({status:"error",error:e}))):A({status:"ok",data:[]}))),X=({query:i,children:e})=>{const[r,n,t]=E(i,I,V);return e(r,n,t)},M=new b(null),Z=M.pipe(y(i=>!!i),u(i=>f(`https://api.opentripmap.com/0.1/ru/places/radius?radius=10000&lon=${i.lng}&lat=${i.lat}&apikey=${R}`).pipe(m(e=>{if(e.ok)return e.json();throw new Error(e.statusText)}),m(e=>A(e.features).pipe(K(),q(4),m(r=>f(`https://api.opentripmap.com/0.1/ru/places/xid/${r.properties.xid}?apikey=${R}`).pipe(u(n=>{if(n.ok)return n.json();throw new Error(n.statusText)}),p(n=>{var t,s;return{id:n.xid,name:n.name,imageSrc:(t=n.preview)==null?void 0:t.source,description:(s=n.wikipedia_extracts)==null?void 0:s.text}}),y(n=>!!n.name))),L(r=>r.name),O())),p(e=>({status:"ok",data:e})),w(async e=>({status:"error",error:e})),v({status:"loading"})))),J=({place:i,children:e})=>{const[r,n,t]=E(i,M,Z);return e(r,n,t)},T=new b(null),ee=T.pipe(y(i=>!!i),u(i=>f(`https://api.openweathermap.org/data/2.5/weather?lat=${i.lat}&lon=${i.lng}&appid=${U}&units=metric`).pipe(m(e=>{if(e.ok)return e.json();throw new Error(e.statusText)}),p(e=>({description:`${Math.floor(e.main.temp)} \xB0C, ${e.weather[0].main}`,iconSrc:`https://openweathermap.org/img/wn/${e.weather[0].icon}@4x.png`})),p(e=>({status:"ok",data:e})),w(async e=>({status:"error",error:e})),v({status:"loading"})))),te=({place:i,children:e})=>{const[r,n,t]=E(i,T,ee);return e(r,n,t)},a=N.exports.jsx,c=N.exports.jsxs,h=N.exports.Fragment,re=()=>{const[i,e]=d.exports.useState(""),[r,n]=d.exports.useState(null);return c("div",{className:"text-center max-w-6xl mx-auto mt-5 p-2",children:[a("div",{className:"text-3xl",children:"Place Info Getter"}),a("div",{className:"mt-3",children:c("div",{className:"grid grid-cols-2 md:grid-cols-12 gap-2",children:[c("div",{className:"rounded col-span-4 bg-white flex flex-col gap-2 p-2",children:[a("input",{className:"border outline-none rounded p-1",type:"text",value:i,onChange:t=>{n(null),e(t.target.value)},placeholder:"Enter address"}),i&&c(h,{children:[a("div",{className:"font-bold",children:"Select a place"}),a(X,{query:i,children:(t,s,o)=>s?a("div",{children:"Loading..."}):o?a("div",{children:o.toString()}):a(h,{children:t==null?void 0:t.map(l=>a("div",{className:`border rounded text-left p-2 hover:bg-gray-100 cursor-pointer ${(r==null?void 0:r.id)===l.id&&"bg-gray-200"}`,onClick:()=>n(l),children:l.title},l.id))})})]})]}),a("div",{className:"rounded col-span-4 bg-white flex flex-col gap-2 p-2",children:r&&c(h,{children:[a("div",{className:"font-bold",children:"What to see here"}),a(J,{place:r,children:(t,s,o)=>o?a("div",{children:o.toString()}):s?a("div",{children:"Loading..."}):t?t.length==0?a("div",{children:"Nothing found"}):c(h,{children:[c("div",{children:["Top ",t.length," spot",t.length==1?"":"s",": "]}),t.map(l=>c("div",{className:"border rounded flex justify-between text-left p-3 gap-2",children:[c("div",{className:"",children:[a("h2",{className:"mb-1 font-bold",children:l.name}),a("p",{className:"text-gray-600 text-sm",children:l.description})]}),l.imageSrc&&a("img",{src:l.imageSrc,className:"h-20 rounded"})]},l.id))]}):a("div",{children:"Enter something"})})]})}),a("div",{className:"rounded col-span-4 bg-white flex flex-col gap-2 p-2",children:r&&c(h,{children:[a("div",{className:"font-bold",children:"Weather"}),a(te,{place:r,children:(t,s,o)=>o?a("div",{children:o.toString()}):s?a("div",{children:"Loading..."}):t?c("div",{className:"flex-col justify-center",children:[t.description,a("img",{className:"mx-auto",src:t.iconSrc})]}):a("div",{children:"Enter something"})})]})})]})})]})};H.render(a(Y.StrictMode,{children:a(re,{})}),document.getElementById("root"));
