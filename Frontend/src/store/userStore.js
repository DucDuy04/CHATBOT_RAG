export const createInitialUserState = () => ({
  users: [],
  selectedUserId: null,
  filters: {},
});

const userStore = createInitialUserState();

export default userStore;
