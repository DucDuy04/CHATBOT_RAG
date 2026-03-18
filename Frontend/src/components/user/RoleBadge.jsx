import Badge from '../ui/Badge.jsx';
import { ROLES } from '../../constants/roles.js';

function RoleBadge({ role = ROLES.USER }) {
  const tone = role === ROLES.ADMIN ? 'success' : 'neutral';
  return <Badge tone={tone}>{role}</Badge>;
}

export default RoleBadge;
