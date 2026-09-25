import axios from 'axios'

const axiosClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

// Attach the JWT (if we have one) to every outgoing request
axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('tai_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// If the backend ever returns 401 (expired/invalid token), clear it and
// send the user back to login instead of leaving them stuck on a broken page
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('tai_token')
      localStorage.removeItem('tai_user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default axiosClient
