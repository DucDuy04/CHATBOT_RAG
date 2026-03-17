import { useState } from 'react';

export function useUsers() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchUsers = async () => {
    setLoading(true);
    setError(null);
    setLoading(false);
    return [];
  };

  const addUser = async (payload) => payload;

  const editUser = async (userId, payload) => ({ userId, payload });

  const deleteUser = async (userId) => userId;

  return {
    users,
    loading,
    error,
    setUsers,
    fetchUsers,
    addUser,
    editUser,
    deleteUser,
  };
}

export default useUsers;
