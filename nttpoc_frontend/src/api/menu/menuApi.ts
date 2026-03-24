import axiosInstance from '@/api/axiosInstance';
import type { ApiResponse, MenuItem } from '@/types';

export async function getMenu(): Promise<MenuItem[]> {
  const res = await axiosInstance.get<ApiResponse<MenuItem[]>>('/menu');
  return res.data.data ?? [];
}
