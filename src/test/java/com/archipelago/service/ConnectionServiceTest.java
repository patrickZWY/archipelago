package com.archipelago.service;

import com.archipelago.exception.ResourceNotFoundException;
import com.archipelago.model.Connection;
import com.archipelago.model.Movie;
import com.archipelago.model.User;
import com.archipelago.repository.ConnectionRepository;
import com.archipelago.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private ConnectionService connectionService;

    private Movie movie1, movie2;
    private User user;
    private Connection connection;

    @BeforeEach
    void setUp() {
        movie1 = Movie.builder().id(1L).title("Inception").build();
        movie2 = Movie.builder().id(2L).title("Interstellar").build();
        user = User.builder().id(1L).email("test@example.com").build();
        connection = Connection.builder()
                .id(1L)
                .fromMovie(movie1)
                .toMovie(movie2)
                .reason("Same director")
                .user(user)
                .build();
    }

    @Test
    void testAddConnection_Success() {
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie1));
        when(movieRepository.findById(2L)).thenReturn(Optional.of(movie2));
        when(connectionRepository.save(any(Connection.class))).thenReturn(connection);

        Connection result = connectionService.addConnection(user, 1L, 2L, "Same director");

        assertNotNull(result);
        assertEquals("Same director", result.getReason());
        verify(movieRepository, times(1)).findById(1L);
        verify(movieRepository, times(1)).findById(2L);
        verify(connectionRepository, times(1)).save(any(Connection.class));
    }

    @Test
    void testAddConnection_MovieNotFound() {
        when(movieRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                connectionService.addConnection(user, 1L, 2L, "Same director"));
        verify(movieRepository, times(1)).findById(1L);
        verify(movieRepository, never()).findById(2L);
    }

    @Test
    void testDeleteConnection_Success() {
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(connection));

        connectionService.deleteConnection(1L);

        verify(connectionRepository, times(1)).findById(1L);
        verify(connectionRepository, times(1)).delete(connection);
    }

    @Test
    void testDeleteConnection_NotFound() {
        when(connectionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> connectionService.deleteConnection(1L));
        verify(connectionRepository, times(1)).findById(1L);
        verify(connectionRepository, never()).delete(any(Connection.class));
    }

    @Test
    void testUpdateConnection_Success() {
        when(connectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any(Connection.class))).thenReturn(connection);

        Connection updated = connectionService.updateConnection(1L, "New reason");

        assertNotNull(updated);
        assertEquals("New reason", updated.getReason());
        verify(connectionRepository, times(1)).findById(1L);
        verify(connectionRepository, times(1)).save(connection);
    }

    @Test
    void testUpdateConnection_NotFound() {
        when(connectionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> connectionService.updateConnection(1L, "New reason"));
        verify(connectionRepository, times(1)).findById(1L);
        verify(connectionRepository, never()).save(any(Connection.class));
    }
}
