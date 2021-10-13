import { useEffect, useState } from 'react';
import { tap } from 'rxjs';
import { fromFetch } from 'rxjs/fetch';
import { GRAPHHOPPER_API_KEY } from '../config';

interface PlaceProviderProps {
  query: string;
  children: (places: Place[], isLoading: boolean, error: Error | null) => JSX.Element;
}

interface Place {

}

export function PlaceProvider({ query, children }: PlaceProviderProps) {
  const [places, setPlaces] = useState<Place[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    console.log(query);
    const data$ = fromFetch(`https://graphhopper.com/api/1/geocode?q=${encodeURIComponent(query)}&locale=ru&debug=true&key=${GRAPHHOPPER_API_KEY}`)
      .pipe(
        tap(console.log)
      );
    
    data$.subscribe();
  }, [query]);

  return children(places, isLoading, error);
}