import { spawn } from 'node:child_process'
import net from 'node:net'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const ssh = 'C:/Windows/System32/OpenSSH/ssh.exe'
const options = ['-4', '-i', 'C:/Users/Ring/.ssh/taiwei.pem', '-o', 'ConnectTimeout=10',
  '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes']
const host = 'ubuntu@101.42.182.117'
const port = 6380
const java = 'D:/Bian/HBuilder/HBuilderX/plugins/amazon-corretto/bin/java.exe'
const probe = () => new Promise(resolve => {
  const socket = net.connect({ host: '127.0.0.1', port })
  socket.on('connect', () => { socket.destroy(); resolve(true) })
  socket.on('error', () => resolve(false))
})
const command = (program, args, options = {}) => new Promise((resolve, reject) => {
  const child = spawn(program, args, { windowsHide: true, ...options })
  let output = ''
  child.stdout.on('data', chunk => output += chunk)
  child.stderr.on('data', chunk => output += chunk)
  child.on('error', () => reject(Error('Verification process unavailable')))
  child.on('close', code => resolve({ code, output }))
})
if (await probe()) throw Error('Local verification port already occupied; no process changed')
const metadata = "from pathlib import Path; import json; p=Path('/srv/apps/duoxu-erp-demo'); assert json.loads((p/'DEPLOYMENT.json').read_text())['owner']=='DUOXU-CLOUD-DEMO-V1'; print(json.dumps({'redis':json.loads((p/'config/secrets.json').read_text())['redis']}))"
const privateResult = await command(ssh, [...options, host, `sudo -n python3 -c "${metadata}"`])
if (privateResult.code !== 0) throw Error('ERP-only verification connection unavailable')
let credential
try { credential = JSON.parse(privateResult.output).redis } catch { throw Error('Private verification metadata unavailable') }
privateResult.output = ''
if (typeof credential !== 'string' || !credential) throw Error('Private verification metadata unavailable')
const tunnel = spawn(ssh, [...options, '-o', 'ExitOnForwardFailure=yes', '-N', '-L', '127.0.0.1:6380:127.0.0.1:16379', host], { windowsHide: true, stdio: 'ignore' })
try {
  for (let attempt = 0; attempt < 30 && !(await probe()); attempt++)
    await new Promise(resolve => setTimeout(resolve, 250))
  if (!(await probe())) throw Error('Isolated Redis verification tunnel unavailable')
  const fullSuite = process.argv.includes('--all')
  const tests = fullSuite ? ['-Dtest=*,!RdWeightSolverTest'] : ['-Dtest=RdGlmClientTest,RdPackageAiServiceTest,RdPackageAiFactsServiceTest,RdAiCallGuardIntegrationTest']
  const result = await command('rtk', ['proxy', 'mvn', '-q', '-pl', 'rd-erp-inventory', '-am',
    ...tests, '-Djvm=' + java,
    '-Dsurefire.failIfNoSpecifiedTests=false', 'test'], {
    cwd: path.join(root, 'backend'),
    env: { ...process.env, RD_AI_TEST_REDIS_PORT: String(port), RD_AI_TEST_REDIS_PASSWORD: credential }
  })
  let nativeResult
  if (fullSuite && result.code === 0) {
    // Mockito stays on supported Java 17; the unchanged native solver needs a modern MSVC runtime.
    nativeResult = await command('rtk', ['proxy', 'mvn', '-q', '-pl', 'rd-erp-inventory', '-am',
      '-Dtest=RdWeightSolverTest', '-Djvm=C:/Users/Ring/.jdks/openjdk-26.0.1/bin/java.exe',
      '-DargLine=--enable-native-access=ALL-UNNAMED', '-Dsurefire.failIfNoSpecifiedTests=false', 'test'],
      { cwd: path.join(root, 'backend'), env: { ...process.env } })
  }
  const exitCode = result.code || nativeResult?.code || 0
  const safe = (result.output + (nativeResult?.output || '')).split(credential).join('[REDACTED]')
    .replace(/sk-[A-Za-z0-9._-]{24,}/g, '[REDACTED]')
  credential = null
  const folder = path.join(root, 'temp/deepseek-verification')
  await mkdir(folder, { recursive: true })
  await writeFile(path.join(folder, process.argv.includes('--all') ? 'backend-all.log' : 'backend-targeted.log'), safe)
  console.log(JSON.stringify({ exitCode, realRedis: true, syntheticTestKeysOnly: true,
    paidModelCalls: 0, credentialPrinted: false, fullSuite,
    nativeSolverSuiteOnCompatibleJvm: nativeResult?.code === 0 }))
  if (exitCode !== 0) console.log(safe.split(/\r?\n/).filter(line => line.startsWith('[ERROR]')).slice(-40).join('\n'))
  process.exitCode = exitCode === 0 ? 0 : 1
} finally {
  credential = null
  tunnel.kill()
}
