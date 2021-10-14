import { useState } from 'react';
import { PlaceProvider, Place } from './components/PlaceProvider';
import { PlaceDataProvider } from './components/PlaceDataProvider';

export const App = () => {
  const [query, setQuery] = useState('');
  const [place, setPlace] = useState<Place | null>(null);

  return (
    <div className="text-center max-w-4xl mx-auto mt-5">
      <div className="text-3xl">
        Place Info Getter
      </div>
      <input 
        className="outline-none rounded p-1 mt-3"
        type="text"
        value={query}
        onChange={e => setQuery(e.target.value)}
        placeholder="Enter address"
      />
      <div className="mt-3">
        <div className="grid grid-cols-6 gap-2">
          <div className="rounded col-span-2 bg-white flex flex-col gap-2 p-2">
            <PlaceProvider query={query}>
              {(places, isLoading, error)  => {
                if (isLoading) {
                  return <div>Loading...</div>;
                } else if (error) {
                  return <div>{error.toString()}</div>;
                } else {
                  return <>{places?.map((place, i) => {
                      return (
                        <div 
                          className="border rounded text-left p-2 hover:bg-gray-100 cursor-pointer"
                          key={i.toString()}
                          onClick={() => setPlace(place)}
                        >
                          {place.title}
                        </div>
                      );
                    })}</>;
                }
              }}
            </PlaceProvider>
          </div>
          <div className="rounded col-span-4 bg-white flex flex-col gap-2 p-2">
            {place &&
                <>
                  <div className="font-bold">{place.title}</div>
                  <PlaceDataProvider place={place}>
                    {(placeData, isLoading, error) => {
                      if (error) {
                        return <div className="red">{error.toString()}</div>
                      } else if (isLoading) {
                        return <div>Loading...</div>;
                      } else {
                        if (!placeData) {
                          return <div>Enter something</div>;
                        }
                        if (placeData.spots.length == 0) {
                          return <div>Nothing found</div>;
                        }
                        return <>
                          <div>Top {placeData.spots.length} spot{placeData.spots.length == 1 ? '' : 's'}: </div>
                          {placeData.spots.map(spot => 
                            <div 
                              className="border rounded text-left p-2"
                              key={spot.name}>
                              {spot.name}
                            </div>
                          )}
                        </>;
                      }
                    }}
                  </PlaceDataProvider>
                </>
            }
          </div>
        </div>
      </div>
    </div>
  )
}