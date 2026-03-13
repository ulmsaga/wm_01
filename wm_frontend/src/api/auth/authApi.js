import axiosInstance from '../axiosInstance';

export async function login(params) {
  const response = await axiosInstance.post('/auth/login', params);
  return response.data;
}