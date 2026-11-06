const b=require('@protomaps/basemaps');const fs=require('fs');
const fonts=new Set();
for (const flavor of ['light','dark']) for (const lang of ['en','ja']) {
  const fl=b.namedFlavor(flavor);
  // Dark roads were barely above the land (#3d3d3d on #1f1f1f): a few steps lighter so they read at night
  if(flavor==='dark') Object.assign(fl,{highway:'#6b6b6b',major:'#5c5c5c',link:'#565656',minor_a:'#4d4d4d',minor_b:'#454545',other:'#424242',minor_service:'#424242',bridges_highway:'#6b6b6b',bridges_major:'#5c5c5c',bridges_link:'#565656',bridges_minor:'#454545',bridges_other:'#424242'});
  const layers=b.layers('protomaps', fl, {lang});
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
