export const collectUtf8Process = (child) => new Promise((resolve,reject)=>{
  child.stdout.setEncoding('utf8')
  child.stderr.setEncoding('utf8')
  let stdout='',stderr=''
  child.stdout.on('data',chunk=>{stdout+=chunk})
  child.stderr.on('data',chunk=>{stderr+=chunk})
  child.on('error',reject)
  child.on('close',code=>resolve({stdout,stderr,code}))
})
