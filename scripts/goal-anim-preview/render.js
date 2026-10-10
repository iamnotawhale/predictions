// node render.js <outDir>  — renders goal/no-goal previews (mp4 + gif) from scene.html via Playwright + ffmpeg
const { chromium } = require('playwright'); const { execFileSync } = require('child_process'); const fs=require('fs'), path=require('path');
const OUT=path.resolve(process.argv[2]||'out'); fs.mkdirSync(OUT,{recursive:true});
const FPS=30, DUR=3.2;
const cases={goal:{home:'MCI',away:'LIV',prev:[1,0],next:[2,0],subtitle:'Haaland  67\'',minute:"67'",disallowed:false},
 nogoal:{home:'MCI',away:'LIV',prev:[2,0],next:[1,0],subtitle:'VAR  ·  offside',minute:"71'",disallowed:true}};
(async()=>{const b=await chromium.launch({executablePath:process.env.CHROME||'/opt/pw-browsers/chromium'});
 for(const [name,c] of Object.entries(cases)){
  const p=await b.newPage({viewport:{width:480,height:480}}); await p.goto('file://'+path.resolve(__dirname,'scene.html'));
  await p.evaluate(()=>document.fonts.ready); await p.evaluate(c=>setup(c),c); const dir=path.join(OUT,'frames-'+name); fs.rmSync(dir,{recursive:true,force:true}); fs.mkdirSync(dir);
  const n=Math.round(FPS*DUR);
  for(let i=0;i<n;i++){await p.evaluate(t=>seek(t),i/FPS); await p.screenshot({path:path.join(dir,String(i).padStart(4,'0')+'.png')});}
  const f=path.join(dir,'%04d.png');
  execFileSync('ffmpeg',['-y','-loglevel','error','-framerate',String(FPS),'-i',f,'-c:v','libx264','-pix_fmt','yuv420p','-crf','20','-movflags','+faststart',path.join(OUT,`goal-${name}.mp4`)]);
  execFileSync('ffmpeg',['-y','-loglevel','error','-framerate',String(FPS),'-i',f,'-vf','fps=25,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4','-loop','0',path.join(OUT,`goal-${name}.gif`)]);
  fs.rmSync(dir,{recursive:true,force:true}); await p.close(); console.log('done',name);}
 await b.close();})();
