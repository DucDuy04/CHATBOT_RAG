import EmptyState from '../ui/EmptyState.jsx';
import { formatDate } from '../../utils/formatDate.js';

function DocumentList({ documents = [] }) {
  if (documents.length === 0) {
    return <EmptyState title="No documents" description="Uploaded documents will appear here." />;
  }

  return (
    <ul>
      {documents.map((document) => (
        <li key={document.id ?? document.name}>
          <span>{document.name}</span>
          <span>{formatDate(document.createdAt)}</span>
        </li>
      ))}
    </ul>
  );
}

export default DocumentList;
