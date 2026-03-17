import EmptyState from '../ui/EmptyState.jsx';
import UserTableRow from './UserTableRow.jsx';

function UserTable({ users = [], onEdit, onDelete }) {
  if (users.length === 0) {
    return <EmptyState title="No users" description="Users will appear here after they are created." />;
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Avatar</th>
          <th>Name</th>
          <th>Email</th>
          <th>Role</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {users.map((user) => (
          <UserTableRow
            key={user.id ?? user.email ?? user.name}
            user={user}
            onEdit={onEdit}
            onDelete={onDelete}
          />
        ))}
      </tbody>
    </table>
  );
}

export default UserTable;
