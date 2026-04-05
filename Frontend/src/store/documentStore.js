export const createInitialDocumentState = () => ({
  documents: [],
  selectedDocumentId: null,
  filters: {},
});

const documentStore = createInitialDocumentState();

export default documentStore;
