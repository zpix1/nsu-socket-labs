import { useEffect, useState } from 'react';
import { BehaviorSubject, startWith, switchMap, filter, of, mergeMap, take, tap, map, mergeAll, toArray, catchError, SchedulerLike, MonoTypeOperatorFunction, Observable, concatMap, asyncScheduler } from 'rxjs';
import { fromFetch } from 'rxjs/fetch';
import { OPENTRIPMAP_API_KEY } from '../config';
import { useRequestObservable, UseRequestObservableResult } from '../utils';
import { Place } from './PlaceProvider';

interface GoodSpot {
  name: string;
}

interface PlaceData {
  spots: GoodSpot[];
  weather: string;
}

interface PlaceDataProviderProps {
  place: Place;
  children: (placeData: PlaceData, isLoading: boolean, error?: Error) => JSX.Element;
}

type Result = UseRequestObservableResult<PlaceData>;

const placeDataSubject = new BehaviorSubject<Place | null>(null);
const placeDataObservable = placeDataSubject.pipe(
  filter(place => !!place),
  switchMap(place => {
    return fromFetch(
      `https://api.opentripmap.com/0.1/ru/places/radius?radius=100&lon=${place!.lng}&lat=${place!.lat}&apikey=${OPENTRIPMAP_API_KEY}`
    ).pipe(
      mergeMap(response => {
        if (response.ok) {
          return response.json()
        }
        throw new Error(response.statusText);
      }),
      mergeMap(responseJson => {
        return of(responseJson.features).pipe(
          mergeAll(),
          take(4),
          mergeMap(feature => {
            return fromFetch(
              `http://api.opentripmap.com/0.1/ru/places/xid/${feature.properties.xid}?apikey=${OPENTRIPMAP_API_KEY}`
            ).pipe(
              switchMap(response => {
                if (response.ok) {
                  return response.json();
                }
                throw new Error(response.statusText);
              }),
              mergeMap(responseJson => of({
                  name: responseJson.name
                } as GoodSpot)
              ),
              filter(spot => !!spot.name)
            )
          }),
          toArray(),
          map(spots => ({ spots, weather: '' } as PlaceData))
        )
      }),
      tap(console.log),
      map(data => ({ status: 'ok', data } as Result)),
      catchError(async error => ({
        status: 'error',
        error
      } as Result)),
      startWith({ status: 'loading' } as Result)
    )
  })
)

export const PlaceDataProvider = ({ place, children }: PlaceDataProviderProps) => {
  const [placeData, isLoading, error] = useRequestObservable(place, placeDataSubject, placeDataObservable);

  return children(
    placeData,
    isLoading,
    error
  );
}