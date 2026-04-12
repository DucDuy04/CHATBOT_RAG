import { getInitials } from '../../utils/getInitials.js';

function Avatar({ name = '', src = '', alt = '' }) {
  if (src) {
    return <img src={src} alt={alt || name || 'Avatar'} />;
  }

  return <span aria-label={name || 'Avatar'}>{getInitials(name)}</span>;
}

export default Avatar;
