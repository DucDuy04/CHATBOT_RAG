export const DEFAULT_ALLOWED_FILE_TYPES = ['application/pdf', 'text/plain'];
export const DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;

export function isValidFileType(file, allowedTypes = DEFAULT_ALLOWED_FILE_TYPES) {
  if (!file) {
    return false;
  }

  return allowedTypes.length === 0 || allowedTypes.includes(file.type);
}

export function isValidFileSize(file, maxSize = DEFAULT_MAX_FILE_SIZE) {
  if (!file) {
    return false;
  }

  return file.size <= maxSize;
}
