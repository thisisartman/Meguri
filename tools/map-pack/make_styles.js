const b=require('@protomaps/basemaps');const fs=require('fs');
const fonts=new Set();
for (const flavor of ['light','dark']) for (const lang of ['en','ja']) {
  const layers=b.layers('protomaps', b.namedFlavor(flavor), {lang});
  layers.forEach(l=>{const f=l.layout&&l.layout['text-font']; if(f) (Array.isArray(f)&&typeof f[0]==='string'?f:JSON.stringify(f).match(/Noto Sans[^"]*/g)||[]).forEach(x=>fonts.add(x));});
  // Plain label layers: one fixed font and the name in the app's language, falling back to the local name.
  // Protomaps' case expressions on text-font and its multi-language format drew no labels in MapLibre Android.
  layers.forEach(l=>{ if(l.type==='symbol' && l.layout && l.layout['text-field']){
    l.layout['text-font']=['NotoSans-Regular'];
    l.layout['text-field']=['coalesce',['get',`name:${lang}`],['get','name']];
  }});
  const style={version:8,glyphs:'asset://glyphs/{fontstack}/{range}.pbf',sprite:`asset://sprites/${flavor}`,
    sources:{protomaps:{type:'vector',url:'pmtiles://__PACK__',attribution:'© OpenStreetMap contributors · Protomaps'}},layers};
  fs.writeFileSync(`style-${flavor}-${lang}.json`,JSON.stringify(style).replace(/Noto Sans (Regular|Medium|Italic)/g,'NotoSans-$1'));
}
console.log([...fonts].join(' | '));
