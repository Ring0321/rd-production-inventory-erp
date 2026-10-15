export const OCR_MULTIPART_LIMIT = 11 * 1024 * 1024;

export async function readOcrUpload(request, { limit = OCR_MULTIPART_LIMIT, timeoutMs = 10000 } = {}) {
  const length = request.headers['content-length'];
  if (length !== undefined && (!/^\d+$/.test(length) || Number(length) > limit)) {
    throw Object.assign(new Error('OCR_UPLOAD_TOO_LARGE'), { status: 413 });
  }
  return new Promise((resolve, reject) => {
    const chunks = [];
    let bytes = 0;
    const finish = (error) => {
      clearTimeout(timer);
      request.off('data', data);
      request.off('end', end);
      request.off('error', failed);
      request.off('aborted', aborted);
      if (error) {
        request.resume();
        reject(error);
      } else resolve(Buffer.concat(chunks, bytes));
    };
    const data = (chunk) => {
      bytes += chunk.length;
      if (bytes > limit) finish(Object.assign(new Error('OCR_UPLOAD_TOO_LARGE'), { status: 413 }));
      else chunks.push(chunk);
    };
    const end = () => finish();
    const failed = () => finish(Object.assign(new Error('OCR_UPLOAD_FAILED'), { status: 400 }));
    const aborted = () => finish(Object.assign(new Error('OCR_UPLOAD_ABORTED'), { status: 400 }));
    const timer = setTimeout(() => finish(Object.assign(new Error('OCR_UPLOAD_TIMEOUT'), { status: 408 })), timeoutMs);
    request.on('data', data).once('end', end).once('error', failed).once('aborted', aborted);
  });
}
