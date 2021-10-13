import { useState } from 'react';
import { PlaceProvider } from './components/PlaceProvider';

export const App = () => {
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
          {(places, isLoading)  => {
            if (isLoading) {
              return <div>Loading...</div>;
            } else  {
              return <>{places.map((place, i) => {
                return <div key={i.toString()}>{place.title}</div>;
              })}</>;
            }
          }}
        </PlaceProvider>
      </div>
    </div>
  )
}