import axios, { AxiosError, type AxiosRequestConfig } from "axios";
import toast from "react-hot-toast";
import type { PaginatedResponse } from "../../types";
import { navigateTo } from "./navigation";

const API_BASE_URL =
  import.meta.env.VITE_APP_API_BASE_URL || "http://localhost:3000/api";

let isRedirecting = false;

// Create Axios instance
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  timeout: 10000,
});

// Optional: Auth token interceptor
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers ??= {} as typeof config.headers;
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Optional: Global logging interceptor
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    console.error("API Error:", status, error.response?.data);

    if (status === 401 && !isRedirecting) {
      isRedirecting = true;
      localStorage.removeItem("token");
      toast.error("Session expired. Please log in again.");
      setTimeout(() => {
        navigateTo("/login");
      }, 100);
      return Promise.reject(error);
    }
    return Promise.reject(error);
  }
);

// Helper to extract error message and show toast
const handleError = (error: unknown, fallbackMessage: string) => {
  const axiosError = error as AxiosError;
  const data = axiosError.response?.data;
  const message =
    (data && typeof data === "object" && "message" in data
      ? (data as { message?: string }).message
      : undefined) ||
    axiosError.message ||
    fallbackMessage;

  toast.error(message);
  return null;
};

// GET request
export const getRequest = async <T>(
  url: string,
  params?: object
): Promise<{ data: T; status: number } | null> => {
  try {
    const response = await api.get<T>(url, { params });
    return { data: response.data, status: response.status };
  } catch (error) {
    return handleError(error, "Error fetching data");
  }
};

//get with pagination items
export const getRequestWithPagination = async <T>(
  url: string,
  params?: object
): Promise<{ data: PaginatedResponse<T>; status: number } | null> => {
  try {
    const response = await api.get<PaginatedResponse<T>>(url, { params });
    return { data: response.data, status: response.status };
  } catch (error) {
    return handleError(error, "Error fetching data");
  }
};

// POST request
export const postRequest = async <T>(
  url: string,
  data?: object,
  options?: AxiosRequestConfig
): Promise<{ data: T; status: number } | null> => {
  try {
    const response = await api.post<T>(url, data, options);
    return { data: response.data, status: response.status };
  } catch (error) {
    return handleError(error, "Error posting data");
  }
};

// PUT request
export const putRequest = async <T>(
  url: string,
  data?: object
): Promise<{ data: T; status: number } | null> => {
  try {
    const response = await api.put<T>(url, data);
    return { data: response.data, status: response.status };
  } catch (error) {
    return handleError(error, "Error updating data");
  }
};

// DELETE request
export const deleteRequest = async <T>(
  url: string
): Promise<{ data: T; status: number } | null> => {
  try {
    const response = await api.delete<T>(url);
    return { data: response.data, status: response.status };
  } catch (error) {
    return handleError(error, "Error deleting data");
  }
};

// PATCH request
export const patchRequest = async <T>(
  url: string,
  data?: object,
  options?: AxiosRequestConfig
): Promise<{ data: T; status: number } | null> => {
  try {
    const response = await api.patch<T>(url, data, options);
    return { data: response.data, status: response.status };
  } catch (error) {
    return handleError(error, "Error patching data");
  }
};

export default api;
