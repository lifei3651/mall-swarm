export function hasSuccessfulUploadMarker(stdout = '', stderr = '') {
  const transcript = `${stdout}\n${stderr}`.replaceAll('\r', '')
  return transcript.split('\n').some(line => line.trim() === '✔ upload')
}
