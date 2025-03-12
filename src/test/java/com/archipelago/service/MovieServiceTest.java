//package com.archipelago.service;
//
//import com.archipelago.exception.ResourceNotFoundException;
//import com.archipelago.model.Movie;
//import com.archipelago.repository.MovieRepository;
//import com.archipelago.service.impl.MovieServiceImpl;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class MovieServiceTest {
//
//    @Mock
//    private MovieRepository movieRepository;
//
//    @InjectMocks
//    private MovieServiceImpl movieService; // Injecting the implementation class
//
//    private Movie sampleMovie;
//
//    @BeforeEach
//    void setUp() {
//        sampleMovie = Movie.builder()
//                .id(1L)
//                .title("Inception")
//                .releaseYear(2010)
//                .director("Christopher Nolan")
//                .pictureUrl("http://example.com/inception.jpg")
//                .build();
//    }
//
//    @Test
//    void testGetMovieById_Found() {
//        when(movieRepository.findById(1L)).thenReturn(Optional.of(sampleMovie));
//
//        Movie result = movieService.getMovieById(1L);
//
//        assertNotNull(result);
//        assertEquals("Inception", result.getTitle());
//        assertEquals(2010, result.getReleaseYear());
//        verify(movieRepository, times(1)).findById(1L);
//    }
//
//    @Test
//    void testGetMovieById_NotFound() {
//        when(movieRepository.findById(1L)).thenReturn(Optional.empty());
//
//        assertThrows(ResourceNotFoundException.class, () -> movieService.getMovieById(1L));
//        verify(movieRepository, times(1)).findById(1L);
//    }
//
//    @Test
//    void testSearchMovies() {
//        when(movieRepository.findByTitleContainingIgnoreCase("Inception"))
//                .thenReturn(List.of(sampleMovie));
//
//        List<Movie> result = movieService.searchMovies("Inception");
//
//        assertNotNull(result);
//        assertEquals(1, result.size());
//        assertEquals("Inception", result.get(0).getTitle());
//        verify(movieRepository, times(1)).findByTitleContainingIgnoreCase("Inception");
//    }
//}
