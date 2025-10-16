import { useState, useEffect, useMemo } from "react";
import {
  getRequestWithPagination,
  patchRequest,
} from "../../components/common/api";
import { useNavigate } from "react-router-dom";
import ConfirmModal from "../../components/common/ConfirmModal";

interface Booking {
  bookingId: string;
  customerName?: string;
  serviceType?: string;
  bookingDate?: string;
  status?: string;
  amount?: number;
  createdAt: Date | string;
}

interface PagedResult<T> {
  items: T[];
  totalCount: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

function BookingManagementPage() {
  const navigate = useNavigate();

  // State per tab
  const [pending, setPending] = useState<PagedResult<Booking>>({
    items: [],
    totalCount: 0,
    totalPages: 0,
    currentPage: 1,
    pageSize: 10,
  });
  const [approved, setApproved] = useState<PagedResult<Booking>>({
    items: [],
    totalCount: 0,
    totalPages: 0,
    currentPage: 1,
    pageSize: 10,
  });
  const [completed, setCompleted] = useState<PagedResult<Booking>>({
    items: [],
    totalCount: 0,
    totalPages: 0,
    currentPage: 1,
    pageSize: 10,
  });

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<
    "pending" | "approved" | "completed"
  >("pending");

  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [currentAction, setCurrentAction] = useState<
    "approve" | "cancel" | null
  >(null);
  const [actionBookingId, setActionBookingId] = useState<string | null>(null);

  const fetchBookings = async (
    tab: "pending" | "approved" | "completed",
    pageNumber = 1,
    pageSize = 10
  ) => {
    try {
      let endpoint = "";
      switch (tab) {
        case "pending":
          endpoint = "/bookings/pending";
          break;
        case "approved":
          endpoint = "/bookings/approved";
          break;
        case "completed":
          endpoint = "/bookings/completed";
          break;
      }

      const response = await getRequestWithPagination<Booking>(endpoint, {
        pageNumber,
        pageSize,
      });

      if (response?.data) {
        switch (tab) {
          case "pending":
            setPending(response.data);
            break;
          case "approved":
            setApproved(response.data);
            break;
          case "completed":
            setCompleted(response.data);
            break;
        }
      }
      return response?.data;
    } catch (error) {
      console.error(`Error fetching ${tab} bookings:`, error);
    }
  };

  useEffect(() => {
    const loadAll = async () => {
      setLoading(true);
      try {
        await Promise.all([
          fetchBookings("pending"),
          fetchBookings("approved"),
          fetchBookings("completed"),
        ]);
      } finally {
        setLoading(false);
      }
    };

    loadAll();
  }, []);

  const activeBookings = useMemo(() => {
    switch (activeTab) {
      case "pending":
        return pending.items;
      case "approved":
        return approved.items;
      case "completed":
        return completed.items;
      default:
        return [];
    }
  }, [activeTab, pending, approved, completed]);

  const handleApproveBooking = (bookingId: string) => {
    setActionBookingId(bookingId);
    setCurrentAction("approve");
    setShowConfirmModal(true);
  };

  const handleCancelBooking = (bookingId: string) => {
    setActionBookingId(bookingId);
    setCurrentAction("cancel");
    setShowConfirmModal(true);
  };

  const confirmAction = async () => {
    if (!actionBookingId || !currentAction) return;

    setActionLoading(actionBookingId);

    try {
      const endpoint =
        currentAction === "approve"
          ? `/bookings/${actionBookingId}/approve`
          : `/bookings/${actionBookingId}/cancel`;

      const response = await patchRequest(endpoint, {});

      if (!response || response.status !== 200)
        throw new Error(`Failed to ${currentAction} booking`);

      // Refresh active tab only
      await fetchBookings(activeTab);
    } catch (error) {
      console.error(`Error ${currentAction} booking:`, error);
    } finally {
      setShowConfirmModal(false);
      setActionLoading(null);
      setCurrentAction(null);
      setActionBookingId(null);
    }
  };

  const cancelAction = () => {
    setShowConfirmModal(false);
    setCurrentAction(null);
    setActionBookingId(null);
  };

  const handlePageChange = (
    tab: "pending" | "approved" | "completed",
    page: number
  ) => {
    setLoading(true);
    fetchBookings(tab, page).finally(() => setLoading(false));
  };

  const getStatusBadge = (status: string) => {
    const statusConfig = {
      pending: { color: "bg-yellow-100 text-yellow-800", label: "Pending" },
      approved: { color: "bg-green-100 text-green-800", label: "Approved" },
      completed: { color: "bg-blue-100 text-blue-800", label: "Completed" },
      finalized: { color: "bg-blue-100 text-blue-800", label: "Completed" },
      cancelled: { color: "bg-red-100 text-red-800", label: "Cancelled" },
    };

    const config =
      statusConfig[status as keyof typeof statusConfig] || statusConfig.pending;

    return (
      <span
        className={`px-3 py-1 rounded-full text-xs font-medium ${config.color}`}
      >
        {config.label}
      </span>
    );
  };

  const formatDate = (dateString: string | Date) => {
    return new Date(dateString).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  };

  const LoadingSpinner = ({ size = "medium" }) => {
    const sizeClass = {
      small: "h-6 w-6",
      medium: "h-16 w-16",
      large: "h-24 w-24",
    }[size];

    return (
      <div className="flex justify-center items-center py-10">
        <div
          className={`animate-spin rounded-full border-t-4 border-blue-600 border-solid ${sizeClass}`}
        ></div>
      </div>
    );
  };

  const BookingCard = ({
    booking,
    showActions = false,
  }: {
    booking: Booking;
    showActions?: boolean;
  }) => (
    <div className="bg-white rounded-xl border border-gray-200 p-6 hover:shadow-lg transition-all duration-300 hover:border-blue-200">
      <div className="flex justify-between items-start mb-4">
        <div>
          <h4 className="font-semibold text-gray-900 text-lg mb-1">
            {booking.customerName || `Booking #${booking.bookingId.slice(-8)}`}
          </h4>
          <p className="text-gray-600 text-sm">
            {booking.serviceType || "General Service"}
          </p>
        </div>
        {getStatusBadge(booking.status?.toLowerCase() || "pending")}
      </div>

      <div className="grid grid-cols-2 gap-10 mb-4 text-sm">
        <div>
          <p className="text-gray-500">Booking ID</p>
          <p className="font-medium text-gray-900">{booking.bookingId}</p>
        </div>
        <div>
          <p className="text-gray-500">Date</p>
          <p className="font-medium text-gray-900">
            {formatDate(booking.createdAt)}
          </p>
        </div>
        {booking.amount && (
          <div className="col-span-2">
            <p className="text-gray-500">Amount</p>
            <p className="font-bold text-lg text-green-600">
              ${booking.amount.toFixed(2)}
            </p>
          </div>
        )}
      </div>

      <div className="flex justify-between items-center pt-4 border-t border-gray-100">
        <button
          onClick={() => navigate(`/admin/bookings/${booking.bookingId}`)}
          className="text-blue-600 hover:text-blue-800 font-medium text-sm flex items-center gap-1 transition-colors"
        >
          View Details
          <svg
            className="w-4 h-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M9 5l7 7-7 7"
            />
          </svg>
        </button>

        {showActions && (
          <div className="flex gap-2">
            <button
              onClick={() => handleApproveBooking(booking.bookingId)}
              disabled={actionLoading === booking.bookingId}
              className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 text-sm font-medium"
            >
              {actionLoading === booking.bookingId ? (
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M5 13l4 4L19 7"
                  />
                </svg>
              )}
              Approve
            </button>
            <button
              onClick={() => handleCancelBooking(booking.bookingId)}
              disabled={actionLoading === booking.bookingId}
              className="bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 text-sm font-medium"
            >
              {actionLoading === booking.bookingId ? (
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              )}
              Cancel
            </button>
          </div>
        )}
      </div>
    </div>
  );

  const TabNavigation = () => (
    <div className="bg-white rounded-xl p-2 shadow-sm border border-gray-200 mb-6">
      <div className="flex space-x-2">
        {[
          { key: "pending", label: "Pending", count: pending.totalCount },
          { key: "approved", label: "Approved", count: approved.totalCount },
          { key: "completed", label: "Completed", count: completed.totalCount },
        ].map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key as typeof activeTab)}
            className={`flex items-center gap-3 px-6 py-3 rounded-lg font-medium transition-all ${
              activeTab === tab.key
                ? "bg-blue-600 text-white shadow-md"
                : "text-gray-600 hover:text-gray-900 hover:bg-gray-100"
            }`}
          >
            <span>{tab.label}</span>
            <span
              className={`px-2 py-1 text-xs rounded-full ${
                activeTab === tab.key
                  ? "bg-blue-500 text-white"
                  : "bg-gray-200 text-gray-600"
              }`}
            >
              {tab.count}
            </span>
          </button>
        ))}
      </div>
    </div>
  );

  const Pagination = ({
    totalPages,
    currentPage,
  }: {
    totalPages: number;
    currentPage: number;
  }) => {
    if (totalPages <= 1) return null;
    const pages = Array.from({ length: totalPages }, (_, i) => i + 1);
    return (
      <div className="flex justify-center mt-6 space-x-2">
        {pages.map((page) => (
          <button
            key={page}
            onClick={() => handlePageChange(activeTab, page)}
            className={`px-3 py-1 rounded-md border ${
              page === currentPage
                ? "bg-blue-600 text-white"
                : "bg-white text-gray-700"
            }`}
          >
            {page}
          </button>
        ))}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-gray-50/30 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-start justify-between">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 mb-2">
                Booking Management
              </h1>
              <p className="text-gray-600">
                Manage and review all booking requests
              </p>
            </div>

            <div className="mt-1">
              <button
                onClick={() => {
                  setActiveTab("pending");
                  navigate("/admin/bookings/pending");
                }}
                className="inline-flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition"
              >
                View Pending Bookings
              </button>
            </div>
          </div>
        </div>

        {/* Stats Overview */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-600 text-sm font-medium">
                  Pending Review
                </p>
                <p className="text-2xl font-bold text-gray-900 mt-1">
                  {pending.totalCount}
                </p>
              </div>
              <div className="w-12 h-12 bg-yellow-100 rounded-lg flex items-center justify-center">
                <svg
                  className="w-6 h-6 text-yellow-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-600 text-sm font-medium">Approved</p>
                <p className="text-2xl font-bold text-gray-900 mt-1">
                  {approved.totalCount}
                </p>
              </div>
              <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
                <svg
                  className="w-6 h-6 text-green-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-xl p-6 shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-600 text-sm font-medium">Completed</p>
                <p className="text-2xl font-bold text-gray-900 mt-1">
                  {completed.totalCount}
                </p>
              </div>
              <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center">
                <svg
                  className="w-6 h-6 text-blue-600"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
            </div>
          </div>
        </div>

        <TabNavigation />

        {loading ? (
          <LoadingSpinner />
        ) : (
          <>
            <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
              {activeBookings.length === 0 ? (
                <div className="col-span-full text-center py-12">
                  <p className="text-gray-500">
                    No {activeTab} bookings found.
                  </p>
                </div>
              ) : (
                activeBookings.map((booking) => (
                  <BookingCard
                    key={booking.bookingId}
                    booking={booking}
                    showActions={activeTab === "pending"}
                  />
                ))
              )}
            </div>

            {/* Pagination */}
            <Pagination
              totalPages={
                activeTab === "pending"
                  ? pending.totalPages
                  : activeTab === "approved"
                  ? approved.totalPages
                  : completed.totalPages
              }
              currentPage={
                activeTab === "pending"
                  ? pending.currentPage
                  : activeTab === "approved"
                  ? approved.currentPage
                  : completed.currentPage
              }
            />
          </>
        )}

        {showConfirmModal && (
          <ConfirmModal
            title={
              currentAction === "approve"
                ? "Confirm Approval"
                : "Confirm Cancellation"
            }
            message={
              currentAction === "approve"
                ? "Are you sure you want to approve this booking?"
                : "Are you sure you want to cancel this booking?"
            }
            confirmText={
              currentAction === "approve" ? "Yes, Approve" : "Yes, Cancel"
            }
            confirmColor={currentAction === "approve" ? "green" : "red"}
            onConfirm={confirmAction}
            onCancel={cancelAction}
            loading={actionLoading === actionBookingId}
          />
        )}
      </div>
    </div>
  );
}

export default BookingManagementPage;
