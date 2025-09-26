import { useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { toast } from "react-hot-toast";
import { postRequest } from "../components/common/api";

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const token = searchParams.get("token");

  const validatePassword = (value: string): boolean => {
    if (!value) {
      setError("Password is required");
      return false;
    } else if (value.length < 6) {
      setError("Password must be at least 6 characters");
      return false;
    }
    setError(null);
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validatePassword(password)) return;
    setIsSubmitting(true);
    try {
      const response = await postRequest("/auth/reset-password", {
        resetToken: token,
        newPassword: password,
      });

      if (response?.status === 200) {
        toast.success("Password reset successful!");
        navigate("/login");
      }
    } catch {
      toast.error("Error resetting password.");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="bg-white p-8 rounded-xl shadow-lg">
          <h2 className="text-xl font-bold text-red-600 mb-4">Invalid Link</h2>
          <p className="text-gray-600">
            Password reset link is invalid or expired.
          </p>
          <div className="mt-4">
            <button
              onClick={() => navigate("/login")}
              className="text-sm text-green-600 hover:text-green-500"
            >
              Back to Login
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-green-50 to-emerald-100">
      <form
        onSubmit={handleSubmit}
        className="bg-white p-8 rounded-xl shadow-lg w-full max-w-md"
        noValidate
      >
        <h2 className="text-2xl font-bold mb-4 text-green-700">
          Reset Password
        </h2>
        <input
          type="password"
          className={`w-full border rounded px-3 py-2 mb-1 ${
            error ? "border-red-500" : ""
          }`}
          placeholder="New password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            if (error) validatePassword(e.target.value);
          }}
        />
        {error && <p className="text-red-500 text-sm mb-3">{error}</p>}
        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-4 w-full bg-green-600 text-white py-2 rounded font-semibold hover:bg-green-700 transition"
        >
          {isSubmitting ? "Resetting..." : "Reset Password"}
        </button>

        <div className="mt-2 text-center">
          <button
            type="button"
            onClick={() => navigate("/login")}
            className="w-full py-2 rounded border text-sm text-green-600 hover:text-green-500"
          >
            Back to Login
          </button>
        </div>
      </form>
    </div>
  );
}
