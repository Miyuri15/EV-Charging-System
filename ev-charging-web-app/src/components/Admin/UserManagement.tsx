import { useState, useEffect } from "react";
import { api } from "../common";
import toast from "react-hot-toast";

// Type definitions - Updated to match API response
interface EVOwner {
  nic: string;
  fullName: string;
  email: string;
  phone: string | null;
  isActive: boolean;
  reactivationRequested?: boolean;
  createdAt?: string;
}

interface Operator {
  _id: string;
  id?: string;
  name?: string;
  Name?: string;
  email: string;
  Email?: string;
  IsActive: boolean;
  isActive?: boolean;
  reactivationRequested?: boolean;
  ReactivationRequested?: boolean;
  CreatedAt?: string;
  stationId?: string;
  stationName?: string;
  stationLocation?: string;
}

interface Station {
  stationId: string;
  name: string;
  location: string;
  type: string;
  capacity: number;
  availableSlots: number;
  isActive: boolean;
}

interface UserDetails {
  nic?: string;
  NIC?: string;
  fullName?: string;
  FullName?: string;
  email?: string;
  Email?: string;
  phone?: string | null;
  Phone?: string;
  isActive?: boolean;
  IsActive?: boolean;
  reactivationRequested?: boolean;
  ReactivationRequested?: boolean;
  createdAt?: string;
  CreatedAt?: string;
  name?: string;
  Name?: string;
  _id?: string;
  id?: string;
  role: "Owner" | "Operator";
  stationId?: string;
  stationName?: string;
  stationLocation?: string;
  StationId?: string;
  StationName?: string;
  StationLocation?: string;
}

interface CreateOperatorForm {
  fullName: string;
  email: string;
  password: string;
  stationId: string;
  stationName: string;
  stationLocation: string;
}

// Pagination interface
interface PaginationInfo {
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
}

function UserManagement() {
  const [reactivationRequests, setReactivationRequests] = useState<EVOwner[]>(
    []
  );
  const [operatorReactivationRequests, setOperatorReactivationRequests] =
    useState<Operator[]>([]);
  const [operators, setOperators] = useState<Operator[]>([]);
  const [owners, setOwners] = useState<EVOwner[]>([]);
  const [stations, setStations] = useState<Station[]>([]);
  const [stationsLoading, setStationsLoading] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>("");
  const [activeTab, setActiveTab] = useState<
    "operators" | "owners" | "reactivations"
  >("operators");
  const [selectedUser, setSelectedUser] = useState<UserDetails | null>(null);
  const [showUserDetails, setShowUserDetails] = useState<boolean>(false);
  const [showCreateOperator, setShowCreateOperator] = useState<boolean>(false);
  const [creatingOperator, setCreatingOperator] = useState<boolean>(false);

  // Pagination states
  const [operatorsPagination, setOperatorsPagination] =
    useState<PaginationInfo>({
      page: 1,
      pageSize: 10,
      totalCount: 0,
      totalPages: 0,
    });

  const [ownersPagination, setOwnersPagination] = useState<PaginationInfo>({
    page: 1,
    pageSize: 10,
    totalCount: 0,
    totalPages: 0,
  });

  const [operatorForm, setOperatorForm] = useState<CreateOperatorForm>({
    fullName: "",
    email: "",
    password: "",
    stationId: "",
    stationName: "",
    stationLocation: "",
  });

  const [showUpdateStation, setShowUpdateStation] = useState<boolean>(false);
  const [updatingStation, setUpdatingStation] = useState<boolean>(false);
  const [
    selectedOperatorForStationUpdate,
    setSelectedOperatorForStationUpdate,
  ] = useState<UserDetails | null>(null);
  const [stationUpdateForm, setStationUpdateForm] = useState({
    stationId: "",
    stationName: "",
    stationLocation: "",
  });

  // Fetch all data on component mount
  useEffect(() => {
    fetchUserData();
  }, []);

  // Fetch data when pagination changes
  useEffect(() => {
    if (activeTab === "operators") {
      fetchOperators();
    } else if (activeTab === "owners") {
      fetchOwners();
    }
  }, [activeTab, operatorsPagination.page, ownersPagination.page]);

  const fetchUserData = async (): Promise<void> => {
    try {
      setLoading(true);
      setError("");

      // Fetch owner reactivation requests
      const ownerReactivationResponse = await api.get(
        "/owners/reactivation-requests"
      );
      console.log(
        "Owner reactivation requests:",
        ownerReactivationResponse?.data
      );
      if (ownerReactivationResponse?.data) {
        setReactivationRequests(ownerReactivationResponse.data);
      }

      // Fetch operator reactivation requests
      const operatorReactivationResponse = await api.get(
        "/operators/reactivation-requests"
      );
      console.log(
        "Operator reactivation requests:",
        operatorReactivationResponse?.data
      );
      if (operatorReactivationResponse?.data) {
        setOperatorReactivationRequests(operatorReactivationResponse.data);
      }

      // Fetch initial operators and owners
      await fetchOperators();
      await fetchOwners();
    } catch (err) {
      const errorMessage = "Failed to fetch user data";
      setError(errorMessage);
      console.error("Error fetching user data:", err);
      toast.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const fetchOperators = async (): Promise<void> => {
    try {
      const { page, pageSize } = operatorsPagination;

      console.log(`Fetching operators: page=${page}, pageSize=${pageSize}`);
      const response = await api.get(
        `/operators?page=${page}&pageSize=${pageSize}`
      );
      console.log("Operators API Response:", response);

      if (response?.data) {
        let operatorsData = [];
        let totalCount = 0;

        if (Array.isArray(response.data)) {
          operatorsData = response.data;
          totalCount = response.data.length;
        } else if (response.data.items && Array.isArray(response.data.items)) {
          operatorsData = response.data.items;
          totalCount = response.data.totalCount || response.data.total || 0;
        } else if (response.data.users && Array.isArray(response.data.users)) {
          operatorsData = response.data.users;
          totalCount = response.data.totalCount || response.data.total || 0;
        } else {
          operatorsData = response.data;
          totalCount = operatorsData.length;
        }

        setOperators(operatorsData);

        if (totalCount === 0 || totalCount === operatorsData.length) {
          if (operatorsData.length === pageSize) {
            totalCount = page * pageSize + 1;
          } else {
            totalCount = (page - 1) * pageSize + operatorsData.length;
          }
        }

        setOperatorsPagination((prev) => ({
          ...prev,
          totalCount: totalCount,
          totalPages: Math.ceil(totalCount / pageSize),
        }));
      }
    } catch (err: any) {
      console.error("Error fetching operators:", err);
      console.error("Error response:", err.response?.data);
      toast.error(err?.response?.data?.message || "Failed to fetch operators");
    }
  };

  const fetchOwners = async (): Promise<void> => {
    try {
      const { page, pageSize } = ownersPagination;

      console.log(`Fetching owners: page=${page}, pageSize=${pageSize}`);
      const response = await api.get(
        `/users?role=Owner&page=${page}&pageSize=${pageSize}`
      );
      console.log("Owners API Response:", response);

      if (response?.data) {
        let ownersData = [];
        let totalCount = 0;

        if (Array.isArray(response.data)) {
          ownersData = response.data;
          totalCount = ownersData.length;
        } else if (response.data.items && Array.isArray(response.data.items)) {
          ownersData = response.data.items;
          totalCount = response.data.totalCount || response.data.total || 0;
        } else if (
          response.data.Owners &&
          Array.isArray(response.data.Owners)
        ) {
          ownersData = response.data.Owners;
          totalCount = response.data.totalCount || response.data.total || 0;
        } else {
          ownersData = response.data;
          totalCount = ownersData.length;
        }

        console.log(
          `Processed ${ownersData.length} owners, total count: ${totalCount}`
        );

        setOwners(ownersData);

        if (totalCount === 0 || totalCount === ownersData.length) {
          if (ownersData.length === pageSize) {
            totalCount = page * pageSize + 1;
          } else {
            totalCount = (page - 1) * pageSize + ownersData.length;
          }
        }

        setOwnersPagination((prev) => ({
          ...prev,
          totalCount: totalCount,
          totalPages: Math.ceil(totalCount / pageSize),
        }));
      }
    } catch (err: any) {
      console.error("Error fetching owners:", err);
      console.error("Error response:", err.response?.data);
      toast.error(err?.response?.data?.message || "Failed to fetch owners");
    }
  };

  const fetchStations = async (): Promise<void> => {
    try {
      setStationsLoading(true);
      const response = await api.get("/station");
      if (response?.data?.items) {
        setStations(response.data.items);
      } else {
        setStations([]);
      }
    } catch (err) {
      console.error("Error fetching stations:", err);
      toast.error("Failed to fetch stations");
    } finally {
      setStationsLoading(false);
    }
  };

  const handleUpdateStationClick = (operator: UserDetails): void => {
    setSelectedOperatorForStationUpdate(operator);
    setStationUpdateForm({
      stationId: operator.stationId || operator.StationId || "",
      stationName: operator.stationName || operator.StationName || "",
      stationLocation:
        operator.stationLocation || operator.StationLocation || "",
    });
    setShowUpdateStation(true);
    fetchStations();
  };

  const handleStationUpdateChange = (stationId: string): void => {
    const selectedStation = stations.find(
      (station) => station.stationId === stationId
    );
    if (selectedStation) {
      setStationUpdateForm({
        stationId: selectedStation.stationId,
        stationName: selectedStation.name,
        stationLocation: selectedStation.location,
      });
    }
  };

  const handleUpdateOperatorStation = async (): Promise<void> => {
    if (!selectedOperatorForStationUpdate || !stationUpdateForm.stationId) {
      toast.error("Please select a station");
      return;
    }

    try {
      setUpdatingStation(true);

      const operatorId =
        selectedOperatorForStationUpdate._id ||
        selectedOperatorForStationUpdate.id;

      if (!operatorId) {
        toast.error("Operator ID not found");
        return;
      }

      const updateData = {
        fullName: getUserDisplayName(selectedOperatorForStationUpdate),
        email: getUserEmail(selectedOperatorForStationUpdate),
        isActive: getUserStatus(selectedOperatorForStationUpdate),
        stationId: stationUpdateForm.stationId,
        stationName: stationUpdateForm.stationName,
        stationLocation: stationUpdateForm.stationLocation,
      };

      const result = await api.put(`/operators/${operatorId}`, updateData);

      if (result?.status === 200) {
        toast.success("Operator station updated successfully");
        setShowUpdateStation(false);
        setSelectedOperatorForStationUpdate(null);

        // Refresh operators list
        await fetchOperators();

        // If we're viewing the operator details, update that too
        if (selectedUser && selectedUser.id === operatorId) {
          setSelectedUser({
            ...selectedUser,
            stationId: stationUpdateForm.stationId,
            stationName: stationUpdateForm.stationName,
            stationLocation: stationUpdateForm.stationLocation,
          });
        }
      } else {
        toast.error(
          result?.data?.message || "Failed to update operator station"
        );
      }
    } catch (err: any) {
      const errorMessage =
        err?.response?.data?.message || "Error updating operator station";
      toast.error(errorMessage);
      console.error("Error updating operator station:", err);
    } finally {
      setUpdatingStation(false);
    }
  };
  // Pagination handlers
  const handleOperatorsPageChange = (newPage: number): void => {
    setOperatorsPagination((prev) => ({ ...prev, page: newPage }));
  };

  const handleOwnersPageChange = (newPage: number): void => {
    setOwnersPagination((prev) => ({ ...prev, page: newPage }));
  };

  const handleStationChange = (stationId: string) => {
    const selectedStation = stations.find(
      (station) => station.stationId === stationId
    );
    if (selectedStation) {
      setOperatorForm({
        ...operatorForm,
        stationId: selectedStation.stationId,
        stationName: selectedStation.name,
        stationLocation: selectedStation.location,
      });
    }
  };

  const handleCreateOperator = async (): Promise<void> => {
    if (
      !operatorForm.fullName ||
      !operatorForm.email ||
      !operatorForm.password ||
      !operatorForm.stationId
    ) {
      toast.error("Please fill all required fields");
      return;
    }

    try {
      setCreatingOperator(true);
      const result = await api.post("/operators", operatorForm);

      if (result?.status === 200 || result?.status === 201) {
        toast.success("Operator created successfully");
        setShowCreateOperator(false);
        setOperatorForm({
          fullName: "",
          email: "",
          password: "",
          stationId: "",
          stationName: "",
          stationLocation: "",
        });

        setOperatorsPagination((prev) => ({ ...prev, page: 1 }));
        await fetchOperators();

        setActiveTab("operators");
      } else {
        toast.error(result?.data?.message || "Failed to create operator");
      }
    } catch (err: any) {
      const errorMessage =
        err?.response?.data?.message || "Error creating operator";
      toast.error(errorMessage);
      console.error("Error creating operator:", err);
    } finally {
      setCreatingOperator(false);
    }
  };

  const handleActivateUser = async (
    id: string,
    isOwner: boolean = false
  ): Promise<void> => {
    try {
      const endpoint = isOwner
        ? `/owners/${id}/activate`
        : `/operators/${id}/status?isActive=true`;
      const result = await api.patch(endpoint);

      if (result?.status === 200) {
        toast.success("User activated successfully");
        if (isOwner) {
          await fetchOwners();
          setActiveTab("owners");
        } else {
          await fetchOperators();
          setActiveTab("operators");
        }
        await fetchUserData(); // Refresh reactivation requests
      } else {
        toast.error(result?.data?.message || "Failed to activate user");
      }
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Error activating user");
      console.error("Error activating user:", err);
    }
  };

  const handleDeactivateUser = async (
    id: string,
    isOwner: boolean = false
  ): Promise<void> => {
    try {
      const endpoint = isOwner
        ? `/owners/${id}/deactivate`
        : `/operators/${id}/status?isActive=false`;
      const result = await api.patch(endpoint);

      if (result?.status === 200) {
        toast.success("User deactivated successfully");
        if (isOwner) {
          await fetchOwners();
        } else {
          await fetchOperators();
        }
      } else {
        toast.error(result?.data?.message || "Failed to deactivate user");
      }
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Error deactivating user");
      console.error("Error deactivating user:", err);
    }
  };

  const handleClearReactivationRequest = async (
    id: string,
    isOwner: boolean = false
  ): Promise<void> => {
    try {
      const endpoint = isOwner
        ? `/owners/${id}/clear-reactivation`
        : `/operators/${id}/clear-reactivation`;
      const result = await api.patch(endpoint);

      if (result?.status === 200) {
        toast.success("Reactivation request cleared successfully");
        await fetchUserData();
        if (isOwner) {
          setActiveTab("owners");
        } else {
          setActiveTab("operators");
        }
      } else {
        toast.error(
          result?.data?.message || "Failed to clear reactivation request"
        );
      }
    } catch (err: any) {
      toast.error(
        err?.response?.data?.message || "Error clearing reactivation request"
      );
      console.error("Error clearing reactivation request:", err);
    }
  };

  const handleViewUserDetails = async (
    user: EVOwner | Operator,
    isOwner: boolean = false
  ): Promise<void> => {
    try {
      if (isOwner) {
        const owner = user as EVOwner;
        const result = await api.get(`/owners/${owner.nic}`);

        if (result?.data) {
          setSelectedUser({ ...result.data, role: "Owner" });
          setShowUserDetails(true);
        }
      } else {
        const operator = user as Operator;
        const userId = operator._id || operator.id;
        if (userId) {
          const result = await api.get(`/operators/${userId}`);

          if (result?.data) {
            setSelectedUser({ ...result.data, role: "Operator" });
            setShowUserDetails(true);
          }
        }
      }
    } catch (err: any) {
      console.error("Error fetching user details:", err);

      // Fallback: Use the available data without making API call
      if (!isOwner) {
        const operator = user as Operator;
        setSelectedUser({
          id: operator._id || operator.id,
          fullName: getUserDisplayName(operator),
          email: getUserEmail(operator),
          role: "Operator",
          isActive: getUserStatus(operator),
          reactivationRequested: getReactivationRequested(operator),
          stationId: operator.stationId,
          stationName: operator.stationName,
          stationLocation: operator.stationLocation,
          createdAt: operator.CreatedAt,
        });
        setShowUserDetails(true);
      } else {
        toast.error(
          err?.response?.data?.message || "Failed to fetch user details"
        );
      }
    }
  };

  // Helper functions
  const getUserDisplayName = (
    user: EVOwner | Operator | UserDetails
  ): string => {
    if ("role" in user) {
      return (
        user.FullName ||
        user.fullName ||
        user.Name ||
        user.name ||
        user.email ||
        "Unknown User"
      );
    } else if ("nic" in user) {
      return user.fullName || "Unknown User";
    } else {
      return user.Name || user.name || user.email || "Unknown User";
    }
  };

  const getUserEmail = (user: EVOwner | Operator | UserDetails): string => {
    if ("role" in user) {
      return user.Email || user.email || "";
    } else if ("nic" in user) {
      return user.email;
    } else {
      return user.email;
    }
  };

  const getUserStatus = (user: EVOwner | Operator | UserDetails): boolean => {
    if ("role" in user) {
      return user.IsActive || user.isActive || false;
    } else if ("nic" in user) {
      return user.isActive;
    } else {
      return user.IsActive || user.isActive || false;
    }
  };

  const getUserPhone = (user: EVOwner | Operator | UserDetails): string => {
    if ("role" in user) {
      return user.Phone || user.phone || "N/A";
    } else if ("nic" in user) {
      return user.phone || "N/A";
    } else {
      return "N/A";
    }
  };

  const getUserNIC = (user: EVOwner | Operator | UserDetails): string => {
    if ("role" in user) {
      return user.NIC || user.nic || "N/A";
    } else if ("nic" in user) {
      return user.nic;
    } else {
      return "N/A";
    }
  };

  const getReactivationRequested = (
    user: EVOwner | Operator | UserDetails
  ): boolean => {
    if ("role" in user) {
      return user.ReactivationRequested || user.reactivationRequested || false;
    } else if ("nic" in user) {
      return user.reactivationRequested || false;
    } else {
      return user.reactivationRequested || user.ReactivationRequested || false;
    }
  };

  const getStationInfo = (user: EVOwner | Operator | UserDetails): string => {
    if ("role" in user && user.role === "Operator") {
      const operator = user as UserDetails;
      const stationName = operator.stationName || operator.StationName;
      const stationLocation =
        operator.stationLocation || operator.StationLocation;

      if (stationName && stationLocation) {
        return `${stationName} - ${stationLocation}`;
      }
      if (stationName) {
        return stationName;
      }
      if (stationLocation) {
        return stationLocation;
      }
      return "No station assigned";
    } else if ("stationName" in user || "stationLocation" in user) {
      const operator = user as Operator;
      if (operator.stationName && operator.stationLocation) {
        return `${operator.stationName} - ${operator.stationLocation}`;
      }
      if (operator.stationName) {
        return operator.stationName;
      }
      if (operator.stationLocation) {
        return operator.stationLocation;
      }
      return "No station assigned";
    }
    return "N/A";
  };

  // Calculate total reactivation requests
  const totalReactivationRequests =
    reactivationRequests.length + operatorReactivationRequests.length;

  // Pagination component
  const Pagination = ({
    pagination,
    onPageChange,
    type,
  }: {
    pagination: PaginationInfo;
    onPageChange: (page: number) => void;
    type: "operators" | "owners";
  }) => {
    const { page, totalPages, totalCount } = pagination;

    if (totalPages <= 1) return null;

    const getPageNumbers = () => {
      const pages = [];
      const maxVisiblePages = 5;

      let startPage = Math.max(1, page - Math.floor(maxVisiblePages / 2));
      let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

      if (endPage - startPage + 1 < maxVisiblePages) {
        startPage = Math.max(1, endPage - maxVisiblePages + 1);
      }

      for (let i = startPage; i <= endPage; i++) {
        pages.push(i);
      }

      return pages;
    };

    return (
      <div className="flex flex-col sm:flex-row justify-between items-center space-y-2 sm:space-y-0 mt-6 p-4 bg-gray-50 rounded-lg">
        <div className="text-sm text-gray-600">
          Showing {(pagination.page - 1) * pagination.pageSize + 1} to{" "}
          {Math.min(pagination.page * pagination.pageSize, totalCount)} of{" "}
          {totalCount} {type}
        </div>

        <div className="flex items-center space-x-1">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 1}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition-colors"
          >
            Previous
          </button>

          {getPageNumbers().map((pageNum) => (
            <button
              key={pageNum}
              onClick={() => onPageChange(pageNum)}
              className={`px-3 py-2 border text-sm font-medium transition-colors ${
                pageNum === page
                  ? "bg-green-600 text-white border-green-600"
                  : "border-gray-300 text-gray-500 hover:bg-gray-50"
              }`}
            >
              {pageNum}
            </button>
          ))}

          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 transition-colors"
          >
            Next
          </button>
        </div>

        <div className="text-sm text-gray-500">
          Page {page} of {totalPages}
        </div>
      </div>
    );
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-lg">Loading user data...</div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-bold text-gray-800">User Management</h2>
        <button
          onClick={() => {
            setShowCreateOperator(true);
            fetchStations();
          }}
          className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 transition-colors"
        >
          Create Operator
        </button>
      </div>

      {/* Reactivation Requests Section */}
      {totalReactivationRequests > 0 && (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold text-yellow-800">
              Reactivation Requests ({totalReactivationRequests})
            </h3>
            <button
              onClick={() => setActiveTab("reactivations")}
              className="bg-yellow-600 text-white px-4 py-2 rounded hover:bg-yellow-700 transition-colors"
            >
              View All
            </button>
          </div>

          {activeTab === "reactivations" ? (
            <div className="space-y-4">
              {/* Owner Reactivation Requests */}
              {reactivationRequests.length > 0 && (
                <div>
                  <h4 className="font-semibold text-yellow-700 mb-2">
                    EV Owners ({reactivationRequests.length})
                  </h4>
                  {reactivationRequests.map((request) => (
                    <div
                      key={request.nic}
                      className="bg-white p-4 rounded border shadow-sm mb-2"
                    >
                      <div className="flex justify-between items-center">
                        <div>
                          <h4 className="font-semibold">{request.fullName}</h4>
                          <p className="text-sm text-gray-600">
                            NIC: {request.nic}
                          </p>
                          <p className="text-sm text-gray-600">
                            Email: {request.email}
                          </p>
                          <p className="text-sm text-gray-600">
                            Phone: {request.phone || "N/A"}
                          </p>
                          <span className="inline-block mt-1 px-2 py-1 text-xs bg-yellow-100 text-yellow-800 rounded">
                            EV Owner
                          </span>
                        </div>
                        <div className="flex flex-col sm:flex-row gap-2">
                          <button
                            onClick={() =>
                              handleActivateUser(request.nic, true)
                            }
                            className="bg-green-600 text-white px-3 py-2 rounded hover:bg-green-700 text-sm transition-colors"
                          >
                            Activate
                          </button>
                          <button
                            onClick={() =>
                              handleClearReactivationRequest(request.nic, true)
                            }
                            className="bg-gray-600 text-white px-3 py-2 rounded hover:bg-gray-700 text-sm transition-colors"
                          >
                            Clear Request
                          </button>
                          <button
                            onClick={() => handleViewUserDetails(request, true)}
                            className="bg-blue-600 text-white px-3 py-2 rounded hover:bg-blue-700 text-sm transition-colors"
                          >
                            View Details
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Operator Reactivation Requests */}
              {operatorReactivationRequests.length > 0 && (
                <div>
                  <h4 className="font-semibold text-yellow-700 mb-2">
                    Operators ({operatorReactivationRequests.length})
                  </h4>
                  {operatorReactivationRequests.map((request) => (
                    <div
                      key={request._id || request.id}
                      className="bg-white p-4 rounded border shadow-sm mb-2"
                    >
                      <div className="flex justify-between items-center">
                        <div>
                          <h4 className="font-semibold">
                            {getUserDisplayName(request)}
                          </h4>
                          <p className="text-sm text-gray-600">
                            Email: {getUserEmail(request)}
                          </p>
                          <p className="text-sm text-gray-600">
                            Station: {getStationInfo(request)}
                          </p>
                          <span className="inline-block mt-1 px-2 py-1 text-xs bg-blue-100 text-blue-800 rounded">
                            Operator
                          </span>
                        </div>
                        <div className="flex flex-col sm:flex-row gap-2">
                          <button
                            onClick={() =>
                              handleActivateUser(
                                request._id || request.id || "",
                                false
                              )
                            }
                            className="bg-green-600 text-white px-3 py-2 rounded hover:bg-green-700 text-sm transition-colors"
                          >
                            Activate
                          </button>
                          <button
                            onClick={() =>
                              handleClearReactivationRequest(
                                request._id || request.id || "",
                                false
                              )
                            }
                            className="bg-gray-600 text-white px-3 py-2 rounded hover:bg-gray-700 text-sm transition-colors"
                          >
                            Clear Request
                          </button>
                          <button
                            onClick={() =>
                              handleViewUserDetails(request, false)
                            }
                            className="bg-blue-600 text-white px-3 py-2 rounded hover:bg-blue-700 text-sm transition-colors"
                          >
                            View Details
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <p className="text-yellow-700">
              {totalReactivationRequests} user(s) have requested account
              reactivation.
              {reactivationRequests.length > 0 &&
                ` (${reactivationRequests.length} owners`}
              {reactivationRequests.length > 0 &&
                operatorReactivationRequests.length > 0 &&
                ", "}
              {operatorReactivationRequests.length > 0 &&
                `${operatorReactivationRequests.length} operators`}
              {reactivationRequests.length > 0 ||
              operatorReactivationRequests.length > 0
                ? ")"
                : ""}
            </p>
          )}
        </div>
      )}

      {/* Tab Navigation */}
      <div className="border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          <button
            onClick={() => {
              setActiveTab("operators");
              setOperatorsPagination((prev) => ({ ...prev, page: 1 }));
            }}
            className={`py-2 px-1 border-b-2 font-medium text-sm ${
              activeTab === "operators"
                ? "border-green-500 text-green-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            } transition-colors`}
          >
            Operators ({operatorsPagination.totalCount})
          </button>
          <button
            onClick={() => {
              setActiveTab("owners");
              setOwnersPagination((prev) => ({ ...prev, page: 1 }));
            }}
            className={`py-2 px-1 border-b-2 font-medium text-sm ${
              activeTab === "owners"
                ? "border-green-500 text-green-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            } transition-colors`}
          >
            Owners ({ownersPagination.totalCount})
          </button>
        </nav>
      </div>

      {/* Operators Tab */}
      {activeTab === "operators" && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h3 className="text-lg font-semibold">Operators</h3>
            <span className="text-sm text-gray-600">
              Page {operatorsPagination.page} • Showing {operators.length}{" "}
              operators
            </span>
          </div>

          {operators.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-gray-600 text-lg">No operators found.</p>
            </div>
          ) : (
            <>
              <div className="grid gap-4">
                {operators.map((operator) => (
                  <div
                    key={operator._id || operator.id}
                    className="bg-white p-4 rounded-lg shadow border hover:shadow-md transition-shadow"
                  >
                    <div className="flex justify-between items-center">
                      <div className="flex-1">
                        <h4 className="font-semibold text-lg text-gray-800 mb-2">
                          {getUserDisplayName(operator)}
                        </h4>

                        <div className="space-y-1">
                          <p className="text-sm text-gray-600">
                            <span className="font-medium">Email:</span>{" "}
                            {getUserEmail(operator)}
                          </p>

                          <p className="text-sm text-gray-600">
                            <span className="font-medium">Station:</span>{" "}
                            {getStationInfo(operator)}
                          </p>

                          <p className="text-sm text-gray-600">
                            <span className="font-medium">Status:</span>{" "}
                            <span
                              className={`ml-1 font-medium ${
                                getUserStatus(operator)
                                  ? "text-green-600"
                                  : "text-red-600"
                              }`}
                            >
                              {getUserStatus(operator) ? "Active" : "Inactive"}
                            </span>
                          </p>
                        </div>

                        {/* Reactivation Request Badge for Operators */}
                        {getReactivationRequested(operator) && (
                          <div className="mt-2">
                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                              Reactivation Requested
                            </span>
                          </div>
                        )}
                      </div>
                      <div className="flex flex-col sm:flex-row gap-2 ml-4">
                        <button
                          onClick={() => handleViewUserDetails(operator, false)}
                          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 text-sm transition-colors flex items-center gap-1"
                        >
                          <span>Details</span>
                        </button>

                        {/* Add this new button for station update */}
                        <button
                          onClick={() =>
                            handleUpdateStationClick({
                              ...operator,
                              role: "Operator",
                              id: operator._id || operator.id,
                              stationId: operator.stationId,
                              stationName: operator.stationName,
                              stationLocation: operator.stationLocation,
                            })
                          }
                          className="bg-purple-600 text-white px-4 py-2 rounded hover:bg-purple-700 text-sm transition-colors flex items-center gap-1"
                        >
                          <span>Change Station</span>
                        </button>

                        {getUserStatus(operator) ? (
                          <button
                            onClick={() =>
                              handleDeactivateUser(
                                operator._id || operator.id || "",
                                false
                              )
                            }
                            className="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700 text-sm transition-colors flex items-center gap-1"
                          >
                            <span>Deactivate</span>
                          </button>
                        ) : (
                          <button
                            onClick={() =>
                              handleActivateUser(
                                operator._id || operator.id || "",
                                false
                              )
                            }
                            className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 text-sm transition-colors flex items-center gap-1"
                          >
                            <span>Activate</span>
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              <Pagination
                pagination={operatorsPagination}
                onPageChange={handleOperatorsPageChange}
                type="operators"
              />
            </>
          )}
        </div>
      )}

      {/* Owners Tab */}
      {activeTab === "owners" && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h3 className="text-lg font-semibold">EV Owners</h3>
            <span className="text-sm text-gray-600">
              Page {ownersPagination.page} • Showing {owners.length} owners
            </span>
          </div>

          {owners.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-gray-600 text-lg">No owners found.</p>
            </div>
          ) : (
            <>
              <div className="grid gap-4">
                {owners.map((owner) => (
                  <div
                    key={owner.nic}
                    className="bg-white p-4 rounded-lg shadow border hover:shadow-md transition-shadow"
                  >
                    <div className="flex justify-between items-center">
                      <div className="flex-1">
                        <h4 className="font-semibold text-lg text-gray-800 mb-2">
                          {owner.fullName}
                        </h4>

                        <div className="space-y-1">
                          <p className="text-sm text-gray-600">
                            <span className="font-medium">Email:</span>{" "}
                            {owner.email}
                          </p>

                          <p className="text-sm text-gray-600">
                            <span className="font-medium">NIC:</span>{" "}
                            {owner.nic}
                          </p>

                          <p className="text-sm text-gray-600">
                            <span className="font-medium">Status:</span>{" "}
                            <span
                              className={`ml-1 font-medium ${
                                owner.isActive
                                  ? "text-green-600"
                                  : "text-red-600"
                              }`}
                            >
                              {owner.isActive ? "Active" : "Inactive"}
                            </span>
                          </p>
                        </div>

                        {owner.reactivationRequested && (
                          <div className="mt-2">
                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                              Reactivation Requested
                            </span>
                          </div>
                        )}
                      </div>

                      <div className="flex flex-col sm:flex-row gap-2 ml-4">
                        <button
                          onClick={() => handleViewUserDetails(owner, true)}
                          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 text-sm transition-colors flex items-center gap-1"
                        >
                          <span>Details</span>
                        </button>
                        {!owner.isActive && (
                          <button
                            onClick={() => handleActivateUser(owner.nic, true)}
                            className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 text-sm transition-colors flex items-center gap-1"
                          >
                            <span>Activate</span>
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              <Pagination
                pagination={ownersPagination}
                onPageChange={handleOwnersPageChange}
                type="owners"
              />
            </>
          )}
        </div>
      )}

      {/* Update Station Modal */}
      {showUpdateStation && selectedOperatorForStationUpdate && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold">Update Operator Station</h3>
              <button
                onClick={() => {
                  setShowUpdateStation(false);
                  setSelectedOperatorForStationUpdate(null);
                }}
                className="text-gray-500 hover:text-gray-700 transition-colors"
              >
                ✕
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Operator
                </label>
                <p className="text-gray-900 font-medium">
                  {getUserDisplayName(selectedOperatorForStationUpdate)}
                </p>
                <p className="text-sm text-gray-600">
                  {getUserEmail(selectedOperatorForStationUpdate)}
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Current Station
                </label>
                <p className="text-gray-900">
                  {getStationInfo(selectedOperatorForStationUpdate)}
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  New Station *
                </label>
                {stationsLoading ? (
                  <div className="flex items-center justify-center p-6">
                    <div className="text-sm text-gray-600">
                      Loading stations...
                    </div>
                  </div>
                ) : (
                  <select
                    value={stationUpdateForm.stationId}
                    onChange={(e) => handleStationUpdateChange(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
                  >
                    <option value="">Select a new station</option>
                    {stations
                      .filter((station) => station.isActive)
                      .map((station) => (
                        <option
                          key={station.stationId}
                          value={station.stationId}
                        >
                          {station.name} - {station.location}
                        </option>
                      ))}
                  </select>
                )}
              </div>

              {stationUpdateForm.stationId && (
                <div className="bg-gray-50 p-3 rounded-md">
                  <h4 className="font-semibold text-sm mb-2">
                    Selected Station:
                  </h4>
                  <p className="text-sm">
                    <strong>Name:</strong> {stationUpdateForm.stationName}
                  </p>
                  <p className="text-sm">
                    <strong>Location:</strong>{" "}
                    {stationUpdateForm.stationLocation}
                  </p>
                </div>
              )}
            </div>

            <div className="mt-6 flex justify-end space-x-3">
              <button
                onClick={() => {
                  setShowUpdateStation(false);
                  setSelectedOperatorForStationUpdate(null);
                }}
                className="bg-gray-600 text-white px-4 py-2 rounded hover:bg-gray-700 transition-colors"
                disabled={updatingStation}
              >
                Cancel
              </button>
              <button
                onClick={handleUpdateOperatorStation}
                disabled={
                  updatingStation ||
                  stationsLoading ||
                  !stationUpdateForm.stationId
                }
                className="bg-purple-600 text-white px-4 py-2 rounded hover:bg-purple-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
              >
                {updatingStation ? "Updating..." : "Update Station"}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* Create Operator Modal */}
      {showCreateOperator && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold">Create New Operator</h3>
              <button
                onClick={() => setShowCreateOperator(false)}
                className="text-gray-500 hover:text-gray-700 transition-colors"
              >
                ✕
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Full Name *
                </label>
                <input
                  type="text"
                  value={operatorForm.fullName}
                  onChange={(e) =>
                    setOperatorForm({
                      ...operatorForm,
                      fullName: e.target.value,
                    })
                  }
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
                  placeholder="Enter full name"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Email *
                </label>
                <input
                  type="email"
                  value={operatorForm.email}
                  onChange={(e) =>
                    setOperatorForm({ ...operatorForm, email: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
                  placeholder="Enter email address"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Password *
                </label>
                <input
                  type="password"
                  value={operatorForm.password}
                  onChange={(e) =>
                    setOperatorForm({
                      ...operatorForm,
                      password: e.target.value,
                    })
                  }
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
                  placeholder="Enter password (min 6 characters)"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Station *
                </label>
                {stationsLoading ? (
                  <div className="flex items-center justify-center p-6">
                    <div className="text-sm text-gray-600">
                      Loading stations...
                    </div>
                  </div>
                ) : (
                  <select
                    value={operatorForm.stationId}
                    onChange={(e) => handleStationChange(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-green-500"
                  >
                    <option value="">Select a station</option>
                    {stations
                      .filter((station) => station.isActive)
                      .map((station) => (
                        <option
                          key={station.stationId}
                          value={station.stationId}
                        >
                          {station.name} - {station.location}
                        </option>
                      ))}
                  </select>
                )}
              </div>

              {operatorForm.stationId && (
                <div className="bg-gray-50 p-3 rounded-md">
                  <h4 className="font-semibold text-sm mb-2">
                    Selected Station:
                  </h4>
                  <p className="text-sm">
                    <strong>Name:</strong> {operatorForm.stationName}
                  </p>
                  <p className="text-sm">
                    <strong>Location:</strong> {operatorForm.stationLocation}
                  </p>
                </div>
              )}
            </div>

            <div className="mt-6 flex justify-end space-x-3">
              <button
                onClick={() => setShowCreateOperator(false)}
                className="bg-gray-600 text-white px-4 py-2 rounded hover:bg-gray-700 transition-colors"
                disabled={creatingOperator}
              >
                Cancel
              </button>
              <button
                onClick={handleCreateOperator}
                disabled={
                  creatingOperator ||
                  stationsLoading ||
                  !operatorForm.fullName ||
                  !operatorForm.email ||
                  !operatorForm.password ||
                  !operatorForm.stationId
                }
                className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
              >
                {creatingOperator ? "Creating..." : "Create Operator"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* User Details Modal */}
      {showUserDetails && selectedUser && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold">User Details</h3>
              <button
                onClick={() => setShowUserDetails(false)}
                className="text-gray-500 hover:text-gray-700 transition-colors"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <label className="font-semibold">Name:</label>
                <p className="mt-1">{getUserDisplayName(selectedUser)}</p>
              </div>
              <div>
                <label className="font-semibold">Email:</label>
                <p className="mt-1">{getUserEmail(selectedUser)}</p>
              </div>
              {selectedUser.role === "Owner" && (
                <>
                  <div>
                    <label className="font-semibold">NIC:</label>
                    <p className="mt-1">{getUserNIC(selectedUser)}</p>
                  </div>
                  <div>
                    <label className="font-semibold">Phone:</label>
                    <p className="mt-1">{getUserPhone(selectedUser)}</p>
                  </div>
                </>
              )}
              {selectedUser.role === "Operator" && (
                <div className="mt-4 pt-4 border-t border-gray-200">
                  <button
                    onClick={() => {
                      setShowUserDetails(false);
                      handleUpdateStationClick(selectedUser);
                    }}
                    className="bg-purple-600 text-white px-4 py-2 rounded hover:bg-purple-700 text-sm transition-colors w-full"
                  >
                    Change Station
                  </button>
                </div>
              )}
              <div>
                <label className="font-semibold">Role:</label>
                <p className="mt-1">{selectedUser.role}</p>
              </div>
              <div>
                <label className="font-semibold">Status:</label>
                <p
                  className={`mt-1 ${
                    getUserStatus(selectedUser)
                      ? "text-green-600"
                      : "text-red-600"
                  }`}
                >
                  {getUserStatus(selectedUser) ? "Active" : "Inactive"}
                </p>
              </div>
              {getReactivationRequested(selectedUser) && (
                <div>
                  <label className="font-semibold text-yellow-600">
                    Reactivation Requested:
                  </label>
                  <p className="mt-1">Yes</p>
                </div>
              )}
              <div>
                <label className="font-semibold">Created At:</label>
                <p className="mt-1">
                  {selectedUser.CreatedAt || selectedUser.createdAt
                    ? new Date(
                        selectedUser.CreatedAt || selectedUser.createdAt || ""
                      ).toLocaleDateString()
                    : "N/A"}
                </p>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                onClick={() => setShowUserDetails(false)}
                className="bg-gray-600 text-white px-4 py-2 rounded hover:bg-gray-700 transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <p className="text-red-800">{error}</p>
        </div>
      )}
    </div>
  );
}

export default UserManagement;
