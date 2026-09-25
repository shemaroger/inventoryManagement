import { createContext, useContext, useState, useCallback } from 'react'
import axiosClient from '../api/axiosClient'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('tai_user')
    return stored ? JSON.parse(stored) : null
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const applySession = useCallback((data) => {
    const { accessToken, ...userData } = data
    localStorage.setItem('tai_token', accessToken)
    localStorage.setItem('tai_user', JSON.stringify(userData))
    setUser(userData)
  }, [])

  // Step 1: verifies credentials and emails a one-time code. Returns the login challenge
  // (no session yet) so the caller can prompt for the code before verifyOtp finishes login.
  const login = useCallback(async (email, password) => {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.post('/auth/login', { email, password })
      return res.data.data
    } catch (err) {
      setError(err.response?.data?.message || 'Login failed')
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  // Step 2: exchanges the emailed code for a real session.
  const verifyOtp = useCallback(async (challengeToken, code) => {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.post('/auth/verify-otp', { challengeToken, code })
      applySession(res.data.data)
      return true
    } catch (err) {
      setError(err.response?.data?.message || 'Verification failed')
      return false
    } finally {
      setLoading(false)
    }
  }, [applySession])

  const resendOtp = useCallback(async (challengeToken) => {
    setLoading(true)
    setError(null)
    try {
      const res = await axiosClient.post('/auth/resend-otp', { challengeToken })
      return res.data.data
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to resend code')
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('tai_token')
    localStorage.removeItem('tai_user')
    setUser(null)
  }, [])

  const hasRole = useCallback(
    (role) => user?.roles?.includes(role) ?? false,
    [user]
  )

  return (
    <AuthContext.Provider
      value={{ user, loading, error, login, verifyOtp, resendOtp, logout, hasRole }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
