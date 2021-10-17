import { useEffect, useState } from 'react';
import { PlaceProvider, Place } from './components/PlaceProvider';
import { PlaceDataProvider } from './components/PlaceDataProvider';
import { WeatherProvider } from './components/WeatherProvider';

export const App = () => {
  const [query, setQuery] = useState('');
  const [currentPlace, setCurrentPlace] = useState<Place | null>(null);

  return (
    <div className="text-center max-w-6xl mx-auto mt-5 p-2">
      <div className="text-3xl">
        Place Info Getter
      </div>
      <div className="mt-3">
        <div className="grid grid-cols-2 md:grid-cols-12 gap-2">
          <div className="rounded col-span-4 bg-white flex flex-col gap-2 p-2">
            <input 
              className="border outline-none rounded p-1 mt-3"
              type="text"
              value={query}
              onChange={e => {
                setCurrentPlace(null);
                setQuery(e.target.value);
              }}
              placeholder="Enter address"
            />
            {query &&
            <>
            <div className="font-bold">Select a place</div>
            <PlaceProvider query={query}>
              {(places, isLoading, error)  => {
                if (isLoading) {
                  return <div>Loading...</div>;
                } else if (error) {
                  return <div>{error.toString()}</div>;
                } else {
                  return <>{places?.map(place => {
                      return (
                        <div 
                          className={`border rounded text-left p-2 hover:bg-gray-100 cursor-pointer ${currentPlace?.id === place.id && 'bg-gray-200'}`}
                          key={place.id}
                          onClick={() => setCurrentPlace(place)}
                        >
                          {place.title}
                        </div>
                      );
                    })}</>;
                }
              }}
            </PlaceProvider>
            </>}
          </div>
          <div className="rounded col-span-4 bg-white flex flex-col gap-2 p-2">
            {currentPlace &&
                <>
                  <div className="font-bold">What to see here</div>
                  <PlaceDataProvider place={currentPlace}>
                    {(spots, isLoading, error) => {
                      if (error) {
                        return <div>{error.toString()}</div>
                      } else if (isLoading) {
                        return <div>Loading...</div>;
                      } else {
                        if (!spots) {
                          return <div>Enter something</div>;
                        }
                        if (spots.length == 0) {
                          return <div>Nothing found</div>;
                        }
                        return <>
                          <div>Top {spots.length} spot{spots.length == 1 ? '' : 's'}: </div>
                          {spots.map(spot =>
                            <div 
                              className="border rounded flex justify-between text-left p-3 gap-2"
                              key={spot.id}
                            >
                              <div className="">
                                  <h2 className="mb-1 font-bold">{spot.name}</h2>
                                  <p className="text-gray-600 text-sm">{spot.description}</p>
                              </div>
                              {spot.imageSrc && <img  
                                src={spot.imageSrc}
                                className="h-20 rounded" 
                              />}
                            </div>
                          )}
                        </>;
                      }
                    }}
                  </PlaceDataProvider>
                </>
            }
          </div>
          <div className="rounded col-span-4 bg-white flex flex-col gap-2 p-2">
            {currentPlace && <>
              <div className="font-bold">Weather</div>
              <WeatherProvider place={currentPlace}>
                {(weather, isLoading, error) => {
                  if (error) {
                    return <div>{error.toString()}</div>
                  } else if (isLoading) {
                    return <div>Loading...</div>;
                  } else {
                    if (!weather) {
                      return <div>Enter something</div>;
                    }
                    return <div className="flex-col justify-center">
                      {weather.description}
                      <img className="mx-auto" src={weather.iconSrc} />
                    </div>;
                  }
                }}
              </WeatherProvider>
            </>}
          </div>
        </div>
      </div>
    </div>
  )
}