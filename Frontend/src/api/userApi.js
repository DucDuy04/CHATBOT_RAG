export async function getUsers() {
  return [];
}

export async function createUser(payload) {
  return payload;
}

export async function updateUser(userId, payload) {
  return { userId, payload };
}

export async function removeUser(userId) {
  return userId;
}

export default {
  getUsers,
  createUser,
  updateUser,
  removeUser,
};
