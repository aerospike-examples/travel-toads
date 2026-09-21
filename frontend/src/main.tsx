import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./index.css";
import App from "./App.tsx";
import { DemoModeProvider } from "./context/DemoModeContext";

createRoot(document.getElementById("root")!).render(
	<StrictMode>
		<BrowserRouter>
			<DemoModeProvider>
				<App />
			</DemoModeProvider>
		</BrowserRouter>
	</StrictMode>,
);
