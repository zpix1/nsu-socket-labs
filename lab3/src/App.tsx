import { useState } from 'react';
import { PlaceProvider } from './components/PlaceProvider';
import { PlaceCard } from './components/PlaceCard';

export const App = () => {
  const [query, setQuery] = useState('');

  return (
    <div className="text-center max-w-4xl mx-auto mt-5">
      <div className="text-3xl">
        Place Info Getter
      </div>
      <input 
        className="border rounded p-1 mt-3"
        type="text"
        value={query}
        onChange={e => setQuery(e.target.value)}
        placeholder="Enter address"
      />
      <div className="mt-3">
        <PlaceProvider query={query}>
          {(places, isLoading)  => {
            if (isLoading) {
              return <div>Loading...</div>;
            } else  {
              return <div className="grid gap-2 auto-rows-fr sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
                {places.map((place, i) => {
                  return (
                    <PlaceCard key={i.toString()} {...place} />
                  );
                })}
              </div>;
            }
          }}
        </PlaceProvider>
      </div>
    </div>
  )
}