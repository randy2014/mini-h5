const DATE_ONLY=/^(\d{4})-(\d{2})-(\d{2})$/
const LOCAL=/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?$/
const OFFSET=/(?:Z|[+-]\d{2}:?\d{2})$/i
const formatter=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'})

function valid(p){const[y,m,d,h=0,i=0,s=0]=p;if(m<1||m>12||h>23||i>59||s>59)return false;const x=new Date(Date.UTC(y,m-1,d));return x.getUTCFullYear()===y&&x.getUTCMonth()===m-1&&x.getUTCDate()===d}
function local(value){let p;if(Array.isArray(value)){if(value.length<3)return null;p=value.slice(0,6).map(Number);while(p.length<6)p.push(0)}else if(typeof value==='string'){const text=value.trim(),m=text.match(LOCAL)||text.match(DATE_ONLY);if(!m||OFFSET.test(text))return null;p=m.slice(1,7).map(x=>Number(x||0))}return p&&valid(p)?p:null}
function instant(value){let ms;if(value instanceof Date)ms=value.getTime();else if(typeof value==='number')ms=Math.abs(value)<1e11?value*1000:value;else if(typeof value==='string'&&/^\d{10,13}$/.test(value.trim()))ms=value.trim().length<=10?Number(value)*1000:Number(value);else if(typeof value==='string'&&OFFSET.test(value.trim()))ms=Date.parse(value.trim());else return null;if(!Number.isFinite(ms))return null;const date=new Date(ms);if(Number.isNaN(date.getTime()))return null;const x=Object.fromEntries(formatter.formatToParts(date).map(v=>[v.type,v.value]));return[x.year,x.month,x.day,x.hour,x.minute,x.second].map(Number)}
function parts(value){return value===null||value===undefined||value===''?null:local(value)||instant(value)}
const pad=value=>String(value).padStart(2,'0')
export function formatDateTime(value){const p=parts(value);return p?`${p[0]}-${pad(p[1])}-${pad(p[2])} ${pad(p[3]||0)}:${pad(p[4]||0)}:${pad(p[5]||0)}`:'-'}
export function formatDate(value){const p=parts(value);return p?`${p[0]}-${pad(p[1])}-${pad(p[2])}`:'-'}
export function toEpochMillis(value){if(value===null||value===undefined||value==='')return null;if(value instanceof Date)return Number.isNaN(value.getTime())?null:value.getTime();if(typeof value==='number')return Number.isFinite(value)?(Math.abs(value)<1e11?value*1000:value):null;if(typeof value==='string'&&/^\d{10,13}$/.test(value.trim()))return value.trim().length<=10?Number(value)*1000:Number(value);if(typeof value==='string'&&OFFSET.test(value.trim())){const ms=Date.parse(value.trim());return Number.isNaN(ms)?null:ms}const p=local(value);return p?Date.UTC(p[0],p[1]-1,p[2],p[3]-8,p[4],p[5]):null}
