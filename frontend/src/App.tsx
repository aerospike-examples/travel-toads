import { Navigate, Route, Routes } from "react-router-dom";
import { Header } from "./components/Header";
import { PresenterPanel } from "./components/PresenterPanel";
import { SearchResultsPage } from "./pages/SearchResultsPage";
import { HotelDetailPage } from "./pages/HotelDetailPage";
import { BookingConfirmationPage } from "./pages/BookingConfirmationPage";

function App() {
  return (
    <>
      <Header />
      <main className="app-main">
        <Routes>
          <Route path="/" element={<SearchResultsPage />} />
          <Route path="/hotels/:hotelId" element={<HotelDetailPage />} />
          <Route path="/confirmation" element={<BookingConfirmationPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <PresenterPanel />
    </>
  );
}

export default App;
