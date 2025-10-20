import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./index.css";
import App from "./App.tsx";
import { Toaster } from "react-hot-toast";

createRoot(document.getElementById("root")!).render(
  <>
    <Toaster />
    <BrowserRouter basename="/EV-Charging-System">
      <App />
    </BrowserRouter>
  </>
);
