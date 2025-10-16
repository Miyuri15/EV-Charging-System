import {
  Routes,
  Route,
  useLocation,
  Navigate,
  useNavigate,
} from "react-router-dom";
import Layout from "./components/Layout";
import AdminDashboard from "./components/Admin/AdminDashboard";
import CSOperatorDashboard from "./components/CSOperator/CSOperatorDashboard";
import NotFound from "./pages/NotFound";
import Unauthorized from "./pages/Unauthorized";
import Home from "./pages/Home";
import Login from "./pages/Login";
import ContactSales from "./pages/ContactSales";
import { useDocumentTitle } from "./hooks/useDocumentTitle";
import Contact from "./pages/Contact";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/common/ProtectedRoute";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import Profile from "./pages/Profile";
import ProtectedLayout from "./components/ProtectedLayout";
import StationList from "./pages/stations/StationList";
import StationForm from "./pages/stations/StationForm";
import StationSlots from "./pages/stations/StationSlots";
import UserManagement from "./components/Admin/UserManagement";
import OperatorStations from "./pages/operator/OperatorStations";
import OperatorSlotManagement from "./pages/operator/OperatorSlotManagement";
import OperatorBookings from "./pages/operator/OperatorBookings";
import BookingManagementPage from "./pages/admin/BookingManagementPage";
import PendingBookingsPage from "./pages/admin/PendingBookingsPage";
import BookingDetailsPage from "./pages/admin/BookingDetailsPage";
import { useEffect } from "react";
import { setNavigate } from "./components/common/navigation";

function App() {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    setNavigate(navigate);
  }, [navigate]);

  // Set document title based on current route
  const getPageTitle = () => {
    switch (location.pathname) {
      case "/admin/dashboard":
        return "Admin Dashboard | EV Charging App";
      case "/operator/dashboard":
        return "CS Operator Dashboard | EV Charging App";
      case "/login":
        return "Login | EV Charging App";
      case "/contact-sales":
        return "Contact Sales | EV Charging App";
      default:
        return "EV Charging Management App";
    }
  };

  useDocumentTitle(getPageTitle());

  return (
    <AuthProvider>
      <Routes>
        {/* Routes without Layout */}
        <Route path="/login" element={<Login />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/contact-sales" element={<ContactSales />} />
        <Route path="/contact" element={<Contact />} />

        {/* All routes inside Layout */}
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/unauthorized" element={<Unauthorized />} />

          {/* Protected Routes */}
          <Route element={<ProtectedRoute requiredRole="admin" />}>
            <Route element={<ProtectedLayout />}>
              <Route path="/admin/dashboard" element={<AdminDashboard />} />
              <Route path="/admin/stations" element={<StationList />} />
              <Route path="/admin/stations/new" element={<StationForm />} />
              <Route
                path="/admin/stations/edit/:stationId"
                element={<StationForm isEdit />}
              />
              <Route
                path="/admin/stations/:stationId/slots"
                element={<StationSlots />}
              />
              <Route path="/admin/Users" element={<UserManagement />} />
              <Route
                path="/admin/bookings"
                element={<BookingManagementPage />}
              />
              <Route
                path="/admin/bookings/pending"
                element={<PendingBookingsPage />}
              />
              <Route
                path="/admin/bookings/:bookingId"
                element={<BookingDetailsPage />}
              />
            </Route>
          </Route>

          <Route element={<ProtectedRoute requiredRole="operator" />}>
            <Route element={<ProtectedLayout />}>
              <Route
                path="/operator/dashboard"
                element={<CSOperatorDashboard />}
              />
              <Route path="/operator/stations" element={<OperatorStations />} />
              <Route
                path="/operator/stations/:stationId/slots"
                element={<OperatorSlotManagement />}
              />
              <Route
                path="/operator/stations/:stationId/bookings"
                element={<OperatorBookings />}
              />
            </Route>
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route element={<ProtectedLayout />}>
              <Route path="/profile" element={<Profile />} />
            </Route>
          </Route>

          {/* Catch-all */}
          <Route path="/not-found" element={<NotFound />} />
          <Route path="*" element={<Navigate to="/not-found" replace />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}

export default App;
