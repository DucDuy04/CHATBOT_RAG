import { useState } from 'react';

export function useDocuments() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const refreshDocuments = async () => {
    setLoading(true);
    setError(null);
    setLoading(false);
    return [];
  };

  const uploadDocument = async (file) => file;

  const removeDocument = async (documentId) => documentId;

  return {
    documents,
    loading,
    error,
    setDocuments,
    refreshDocuments,
    uploadDocument,
    removeDocument,
  };
}

export default useDocuments;
