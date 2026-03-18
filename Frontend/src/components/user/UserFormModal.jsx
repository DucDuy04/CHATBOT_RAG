import Button from '../ui/Button.jsx';
import Modal from '../ui/Modal.jsx';
import { ROLES } from '../../constants/roles.js';

function UserFormModal({
  open = false,
  title = 'Add user',
  values = {},
  onChange,
  onSubmit,
  onClose,
}) {
  const footer = (
    <div>
      <Button type="button" variant="ghost" onClick={onClose}>
        Cancel
      </Button>
      <Button type="button" onClick={() => onSubmit?.(values)}>
        Save
      </Button>
    </div>
  );

  return (
    <Modal open={open} title={title} footer={footer} onClose={onClose}>
      <div>
        <label>
          <span>Name</span>
          <input
            type="text"
            value={values.name ?? ''}
            onChange={(event) => onChange?.('name', event.target.value)}
          />
        </label>
        <label>
          <span>Email</span>
          <input
            type="email"
            value={values.email ?? ''}
            onChange={(event) => onChange?.('email', event.target.value)}
          />
        </label>
        <label>
          <span>Role</span>
          <select
            value={values.role ?? ROLES.USER}
            onChange={(event) => onChange?.('role', event.target.value)}
          >
            <option value={ROLES.ADMIN}>{ROLES.ADMIN}</option>
            <option value={ROLES.USER}>{ROLES.USER}</option>
          </select>
        </label>
      </div>
    </Modal>
  );
}

export default UserFormModal;
