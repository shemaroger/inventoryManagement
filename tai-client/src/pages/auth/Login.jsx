import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2, Mail, Lock, ShieldCheck, ArrowLeft } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'

export default function Login() {
  const { login, verifyOtp, resendOtp, loading, error } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [challenge, setChallenge] = useState(null)
  const [code, setCode] = useState('')
  const [resent, setResent] = useState(false)

  const handleLogin = async (e) => {
    e.preventDefault()
    setResent(false)
    const result = await login(email, password)
    if (result) setChallenge(result)
  }

  const handleVerify = async (e) => {
    e.preventDefault()
    const success = await verifyOtp(challenge.challengeToken, code)
    if (success) navigate('/')
  }

  const handleResend = async () => {
    setResent(false)
    const result = await resendOtp(challenge.challengeToken)
    if (result) {
      setChallenge(result)
      setCode('')
      setResent(true)
    }
  }

  const backToCredentials = () => {
    setChallenge(null)
    setCode('')
    setResent(false)
  }

  return (
    <div
      className="flex min-h-screen items-center justify-center bg-white bg-contain bg-center bg-no-repeat px-4 py-10"
      style={{ backgroundImage: "url('/background.jpg')" }}
    >
      <div className="w-full max-w-sm rounded-2xl border border-navy-100 bg-white/95 p-8 shadow-xl backdrop-blur-sm">
        <div className="flex flex-col items-center">
          <img src="/logo.png" alt="Mapleco" className="h-16 w-16 rounded-xl object-contain" />
          <h1 className="mt-3 text-2xl font-bold tracking-wide text-navy-800">TAI</h1>
          <p className="text-center text-sm text-navy-400">Inventory management for Mapleco S.A.R.L</p>
        </div>

        {!challenge ? (
          <form onSubmit={handleLogin} className="mt-8 space-y-6">
            <div className="flex items-center gap-3 border-b border-navy-200 pb-2 focus-within:border-orange-500">
              <Mail size={18} className="shrink-0 text-navy-400" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full bg-transparent text-sm text-navy-800 placeholder:text-navy-400 focus:outline-none"
                placeholder="Email"
                autoComplete="username"
              />
            </div>

            <div className="flex items-center gap-3 border-b border-navy-200 pb-2 focus-within:border-orange-500">
              <Lock size={18} className="shrink-0 text-navy-400" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-transparent text-sm text-navy-800 placeholder:text-navy-400 focus:outline-none"
                placeholder="Password"
                autoComplete="current-password"
              />
            </div>

            {error && (
              <div className="rounded-md border border-orange-300 bg-orange-50 px-3 py-2 text-sm text-orange-700">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-2 rounded-full bg-orange-500 px-4 py-3 text-sm font-semibold text-white transition-colors hover:bg-orange-600 disabled:opacity-60"
            >
              {loading && <Loader2 size={16} className="animate-spin" />}
              Sign in
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerify} className="mt-8 space-y-6">
            <div className="flex flex-col items-center gap-2 text-center">
              <ShieldCheck size={28} className="text-orange-500" />
              <p className="text-sm text-navy-600">
                We sent a 6-digit code to <span className="font-medium text-navy-800">{challenge.maskedEmail}</span>
              </p>
            </div>

            <div className="flex items-center gap-3 border-b border-navy-200 pb-2 focus-within:border-orange-500">
              <input
                type="text"
                inputMode="numeric"
                pattern="\d{6}"
                maxLength={6}
                required
                autoFocus
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                className="w-full bg-transparent text-center text-lg tracking-[0.5em] text-navy-800 placeholder:text-navy-400 placeholder:tracking-normal focus:outline-none"
                placeholder="------"
              />
            </div>

            {error && (
              <div className="rounded-md border border-orange-300 bg-orange-50 px-3 py-2 text-sm text-orange-700">
                {error}
              </div>
            )}
            {resent && !error && (
              <div className="rounded-md border border-navy-200 bg-navy-50 px-3 py-2 text-sm text-navy-600">
                A new code has been sent.
              </div>
            )}

            <button
              type="submit"
              disabled={loading || code.length !== 6}
              className="flex w-full items-center justify-center gap-2 rounded-full bg-orange-500 px-4 py-3 text-sm font-semibold text-white transition-colors hover:bg-orange-600 disabled:opacity-60"
            >
              {loading && <Loader2 size={16} className="animate-spin" />}
              Verify
            </button>

            <div className="flex items-center justify-between text-sm">
              <button
                type="button"
                onClick={backToCredentials}
                className="flex items-center gap-1 text-navy-400 hover:text-navy-600"
              >
                <ArrowLeft size={14} /> Back
              </button>
              <button
                type="button"
                onClick={handleResend}
                disabled={loading}
                className="font-medium text-orange-600 hover:text-orange-700 disabled:opacity-60"
              >
                Resend code
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
