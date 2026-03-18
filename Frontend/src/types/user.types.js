import { ROLES } from '../constants/roles.js';

export const userShape = {
  id: '',
  name: '',
  email: '',
  role: ROLES.USER,
  avatarUrl: '',
};