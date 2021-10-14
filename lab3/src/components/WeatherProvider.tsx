import { useEffect, useState } from 'react';
import { BehaviorSubject, startWith, switchMap, filter, of, mergeMap, take, tap, map, mergeAll, toArray, catchError, distinctUntilKeyChanged, MonoTypeOperatorFunction, Observable, concatMap, asyncScheduler, distinct, distinctUntilChanged } from 'rxjs';
import { fromFetch } from 'rxjs/fetch';
import { OPENTRIPMAP_API_KEY, OPENWEATHERMAP_API_KEY } from '../config';
import { useRequestObservable, UseRequestObservableResult } from '../utils';
import { Place } from './PlaceProvider';

interface Weather {
  description: string;
  iconSrc: string;
}

interface WeatherProviderProps {
  place: Place;
  children: (weather: Weather | null, isLoading: boolean, error: Error | null) => JSX.Element;
}

type Result = UseRequestObservableResult<Weather>;

const weatherSubject = new BehaviorSubject<Place | null>(null);
const weatherObservable = weatherSubject.pipe(
  filter(place => !!place),
  switchMap(place => {
    return fromFetch(
      `https://api.openweathermap.org/data/2.5/weather?lat=${place!.lng}&lon=${place!.lat}&appid=${OPENWEATHERMAP_API_KEY}`
    ).pipe(
      mergeMap(response => {
        if (response.ok) {
          return response.json()
        }
        throw new Error(response.statusText);
      }),
      map(responseJson => ({
        description: `${Math.floor(responseJson.main.temp - 273)} °C`,
        iconSrc: `http://openweathermap.org/img/wn/${responseJson.weather[0].icon}@4x.png`
      } as Weather)),
      map(data => ({ status: 'ok', data } as Result)),
      catchError(async error => ({
        status: 'error',
        error
      } as Result)),
      startWith({ status: 'loading' } as Result)
    )
  })
)

export const WeatherProvider = ({ place, children }: WeatherProviderProps) => {
  const [weather, isLoading, error] = useRequestObservable(place, weatherSubject, weatherObservable);

  return children(
    weather,
    isLoading,
    error
  );
}