# Place search data-layer API

Call `PlaceRepository.searchPlaces(query)` after the user explicitly submits a search.
Do not call it on each keystroke: the public Nominatim service prohibits autocomplete.

The factory already returns the implementation:

```kotlin
val repository = PlaceRepositoryFactory.create(applicationContext)
val result = repository.searchPlaces("Box Hill, Victoria, Australia")
result.onSuccess { places ->
    // Display name/displayName for selection. Pass the selected result's
    // coordinates.latitude and coordinates.longitude to the UV repository.
}
result.onFailure { error ->
    // Show a search error; do not invent a location or fall back to GPS silently.
}
```

Results use `PlaceSearchResult(name, displayName, coordinates)`. An empty successful
list means no matches. Blank/overlong queries and invalid response coordinates
return failure. Coroutine cancellation is propagated.

Search uses Nominatim `/search`, JSON v2, up to five results. Queries are trimmed
and whitespace-normalized; case-insensitive results are cached for 24 hours,
including empty results, with a capacity of 64 queries per repository instance.
This is an in-memory cache, not a Room cache, and is lost on process restart.
Search and reverse geocoding share the existing one-request-per-second limiter
and identifying User-Agent. The limiter is process-local, not an aggregate limit
across installed devices. Public Nominatim limits apply to the whole application.

The consuming UI must display `NominatimClient.ATTRIBUTION` and respect the
[Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).
Attribution, deployment-wide limits, and provider switching must be considered
before a wider release.

## Current UI integration

`UVAppRoot` creates `PlaceRepositoryFactory` and injects the repository into
`MainViewModel`. The search dialog submits through `onSearchSubmit()` when the
user uses the keyboard Search action; changing the query only updates UI state.
The ViewModel cancels the previous search and displays searching, no-results or
failure status as appropriate. The dialog renders returned `displayName` values
and displays OpenStreetMap attribution through `PlaceDataAttribution`.

Selecting a result calls `onPlaceSelected(PlaceSearchResult)`, closes the dialog
and observes/refreshes the UV forecast using that result's coordinates. The
selected location is marked approximate so indoor-location logic does not treat
it as the user's measured position. Search failure does not automatically switch
to GPS; "Use current location" is a separate user action through the location
permission flow.
