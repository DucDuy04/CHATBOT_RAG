import Avatar from '../ui/Avatar.jsx';
import RoleBadge from './RoleBadge.jsx';

function UserTableRow({ user, onEdit, onDelete }) {
  return (
    <tr>
      <td>
        <Avatar name={user?.name} src={user?.avatarUrl} />
      </td>
      <td>{user?.name || 'Unknown user'}</td>
      <td>{user?.email || '-'}</td>
      <td>
        <RoleBadge role={user?.role} />
      </td>
      <td>
        <button type="button" onClick={() => onEdit?.(user)}>
          Edit
        </button>
        <button type="button" onClick={() => onDelete?.(user)}>
          Delete
        </button>
      </td>
    </tr>
  );
}

export default UserTableRow;
