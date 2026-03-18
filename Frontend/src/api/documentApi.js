export async function getDocuments() {
  return [];
}

export async function uploadDocument(file) {
  return file;
}

export async function deleteDocument(documentId) {
  return documentId;
}

export default {
  getDocuments,
  uploadDocument,
  deleteDocument,
};
