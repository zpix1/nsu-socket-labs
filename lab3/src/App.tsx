import { useState } from 'react';
import { PlaceProvider } from './components/PlaceProvider';

export default function App() {
  const [query, setQuery] = useState('');

  return (
    <div className="text-center max-w-md mx-auto mt-5">
      <div className="text-3xl">
        Place Info Getter
      </div>
      <input 
        className="border rounded p-1 mt-3"
        type="text"
        value={query}
        onChange={e => setQuery(e.target.value)}
        placeholder="enter address"
      />
      <div className="mt-3">
        <PlaceProvider query={query}>
          {(places, isLoading, error)  => {
            if (error) {
              return <div>Error: {error}</div>;
            } else if (isLoading) {
              return <div>Loading...</div>;
            } else  {
              return <>{places.map(place => <div>kek</div>)}</>;
            }
          }}
        </PlaceProvider>
      </div>
    </div>
  )
}