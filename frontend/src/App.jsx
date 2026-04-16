import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

function apiBase() {
  const v = import.meta.env.VITE_API_BASE;
  if (v != null && String(v).trim() !== '') {
    return String(v).replace(/\/$/, '');
  }
  return '';
}

function wsBase() {
  const v = import.meta.env.VITE_WS_BASE;
  if (v != null && String(v).trim() !== '') {
    return String(v).replace(/\/$/, '');
  }
  if (typeof window === 'undefined') {
    return '';
  }
  return `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}`;
}

const defaultRoom = { id: null, name: null, type: null, participants: [] };

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('chat_token') || '');
  const [username, setUsername] = useState(() => localStorage.getItem('chat_user') || '');
  const [mode, setMode] = useState('login');
  const [loginState, setLoginState] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [rooms, setRooms] = useState([]);
  const [selectedRoom, setSelectedRoom] = useState(defaultRoom);
  const [messages, setMessages] = useState([]);
  const [newRoomName, setNewRoomName] = useState('');
  const [newRoomParticipants, setNewRoomParticipants] = useState('');
  const [dmUsername, setDmUsername] = useState('');
  const [newMessage, setNewMessage] = useState('');
  const [connected, setConnected] = useState(false);
  const [userQuery, setUserQuery] = useState('');
  const [userSuggestions, setUserSuggestions] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [showInvitations, setShowInvitations] = useState(false);
  const [showIpAddresses, setShowIpAddresses] = useState(false);
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const wsRef = useRef(null);
  const selectedRoomIdRef = useRef(null);
  const messagesEndRef = useRef(null);
  const searchTimerRef = useRef(null);

  useEffect(() => {
    selectedRoomIdRef.current = selectedRoom?.id ?? null;
  }, [selectedRoom?.id]);

  useEffect(() => {
    if (token) {
      localStorage.setItem('chat_token', token);
      localStorage.setItem('chat_user', username);
    }
  }, [token, username]);

  const authHeaders = useMemo(() => {
    return token ? { Authorization: `Bearer ${token}` } : {};
  }, [token]);

  const apiRequest = useCallback(
    async (path, options = {}) => {
      const base = apiBase();
      const response = await fetch(`${base}${path}`, {
        headers: {
          'Content-Type': 'application/json',
          ...authHeaders,
          ...options.headers
        },
        ...options
      });
      const text = await response.text();
      if (!response.ok) {
        let msg = text || response.statusText;
        try {
          const j = JSON.parse(text);
          if (j?.message) {
            msg = j.message;
          }
        } catch {
          /* plain text */
        }
        throw new Error(msg);
      }
      if (!text) {
        return null;
      }
      return JSON.parse(text);
    },
    [authHeaders]
  );

  const fetchRooms = useCallback(async () => {
    const data = await apiRequest('/api/rooms');
    setRooms(data || []);
    setSelectedRoom(prev => {
      if (prev?.id && data?.some(r => r.id === prev.id)) {
        return data.find(r => r.id === prev.id);
      }
      if (data?.length) {
        return data[0];
      }
      return defaultRoom;
    });
  }, [apiRequest]);

  const fetchInvitations = useCallback(async () => {
    try {
      const data = await apiRequest('/api/invitations');
      setInvitations(data || []);
    } catch {
      setInvitations([]);
    }
  }, [apiRequest]);

  const connectWebSocket = useCallback(() => {
    if (!token) {
      return;
    }
    const prev = wsRef.current;
    if (prev) {
      prev.close();
    }
    const url = `${wsBase()}/ws/chat?token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.addEventListener('open', () => setConnected(true));

    ws.addEventListener('message', event => {
      try {
        const payload = JSON.parse(event.data);
        if (payload?.roomId !== selectedRoomIdRef.current) {
          return;
        }
        setMessages(prev => {
          if (payload?.id != null && prev.some(m => m.id === payload.id)) {
            return prev;
          }
          return [...prev, payload];
        });
      } catch {
        /* ignore */
      }
    });

    ws.addEventListener('close', () => setConnected(false));
    ws.addEventListener('error', () => setConnected(false));
  }, [token]);

  useEffect(() => {
    if (!token) {
      return undefined;
    }
    let cancelled = false;
    (async () => {
      try {
        await fetchRooms();
        await fetchInvitations();
        if (!cancelled) {
          connectWebSocket();
        }
      } catch {
        if (!cancelled) {
          setError('Не удалось загрузить комнаты.');
        }
      }
    })();
    return () => {
      cancelled = true;
      const w = wsRef.current;
      if (w) {
        w.close();
      }
    };
  }, [token, fetchRooms, fetchInvitations, connectWebSocket]);

  useEffect(() => {
    if (!selectedRoom?.id) {
      setMessages([]);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        let url = `/api/rooms/${selectedRoom.id}/messages`;
        const params = new URLSearchParams();
        if (dateFrom) params.append('from', new Date(dateFrom).toISOString());
        if (dateTo) params.append('to', new Date(dateTo).toISOString());
        if (params.toString()) url += `?${params.toString()}`;

        const data = await apiRequest(url);
        if (!cancelled) {
          setMessages(data || []);
        }
      } catch {
        if (!cancelled) {
          setError('Не удалось загрузить сообщения.');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedRoom?.id, dateFrom, dateTo, apiRequest]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (!token || !userQuery.trim()) {
      setUserSuggestions([]);
      return undefined;
    }
    if (searchTimerRef.current) {
      clearTimeout(searchTimerRef.current);
    }
    searchTimerRef.current = setTimeout(async () => {
      try {
        const q = encodeURIComponent(userQuery.trim());
        const list = await apiRequest(`/api/users?q=${q}`);
        setUserSuggestions(Array.isArray(list) ? list : []);
      } catch {
        setUserSuggestions([]);
      }
    }, 280);
    return () => clearTimeout(searchTimerRef.current);
  }, [userQuery, token, apiRequest]);

  const handleAuth = async action => {
    setError('');
    try {
      const payload = {
        username: loginState.username.trim(),
        password: loginState.password
      };
      const data = await apiRequest(`/api/auth/${action}`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      setToken(data.token);
      setUsername(data.username);
    } catch (err) {
      setError(err.message || 'Ошибка аутентификации');
    }
  };

  const createRoom = async () => {
    if (!newRoomName.trim()) {
      setError('Укажите название группы.');
      return;
    }
    setError('');
    try {
      const data = await apiRequest('/api/rooms', {
        method: 'POST',
        body: JSON.stringify({
          name: newRoomName.trim(),
          type: 'GROUP',
          participants: newRoomParticipants
            .split(/[,\n]/)
            .map(item => item.trim())
            .filter(Boolean)
        })
      });
      setRooms(prev => {
        const next = [data, ...prev.filter(r => r.id !== data.id)];
        return next;
      });
      setNewRoomName('');
      setNewRoomParticipants('');
      setSelectedRoom(data);
    } catch (err) {
      setError(err.message || 'Не удалось создать комнату.');
    }
  };

  const openDirect = async () => {
    const u = dmUsername.trim();
    if (!u) {
      setError('Введите имя пользователя для личного чата.');
      return;
    }
    setError('');
    try {
      const data = await apiRequest('/api/rooms/dm', {
        method: 'POST',
        body: JSON.stringify({ withUsername: u })
      });
      setDmUsername('');
      setRooms(prev => {
        const exists = prev.some(r => r.id === data.id);
        if (exists) {
          return prev.map(r => (r.id === data.id ? data : r));
        }
        return [data, ...prev];
      });
      setSelectedRoom(data);
    } catch (err) {
      setError(err.message || 'Не удалось открыть чат.');
    }
  };

  const sendMessage = () => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      setError('Нет соединения с чатом. Подождите или обновите страницу.');
      return;
    }
    if (!newMessage.trim() || !selectedRoom.id) {
      return;
    }
    setError('');
    wsRef.current.send(
      JSON.stringify({
        roomId: selectedRoom.id,
        content: newMessage.trim(),
        fileUrl: null
      })
    );
    setNewMessage('');
  };

  const logout = () => {
    setToken('');
    setUsername('');
    localStorage.removeItem('chat_token');
    localStorage.removeItem('chat_user');
    setRooms([]);
    setSelectedRoom(defaultRoom);
    setMessages([]);
    setError('');
    setUserSuggestions([]);
    setUserQuery('');
    setInvitations([]);
    if (wsRef.current) {
      wsRef.current.close();
    }
  };

  const acceptInvitation = async invitationId => {
    setError('');
    try {
      await apiRequest(`/api/invitations/${invitationId}/accept`, { method: 'POST' });
      await fetchInvitations();
      await fetchRooms();
    } catch (err) {
      setError(err.message || 'Не удалось принять приглашение.');
    }
  };

  const declineInvitation = async invitationId => {
    setError('');
    try {
      await apiRequest(`/api/invitations/${invitationId}/decline`, { method: 'POST' });
      await fetchInvitations();
    } catch (err) {
      setError(err.message || 'Не удалось отклонить приглашение.');
    }
  };

  const kickParticipant = async targetUsername => {
    if (!selectedRoom?.id) {
      return;
    }
    setError('');
    try {
      await apiRequest(`/api/rooms/${selectedRoom.id}/kick`, {
        method: 'POST',
        body: JSON.stringify({ username: targetUsername })
      });
      await fetchRooms();
    } catch (err) {
      setError(err.message || 'Не удалось удалить участника.');
    }
  };

  const formatTime = iso => {
    if (!iso) {
      return '';
    }
    try {
      return new Date(iso).toLocaleString('ru-RU', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return '';
    }
  };

  const pickSuggestion = name => {
    setUserQuery('');
    setUserSuggestions([]);
    setNewRoomParticipants(prev => {
      const parts = prev
        .split(/[,\n]/)
        .map(s => s.trim())
        .filter(Boolean);
      if (parts.includes(name)) {
        return parts.join(', ');
      }
      return [...parts, name].join(', ');
    });
  };

  const roomInitial = name => (name && name.trim()[0] ? name.trim()[0].toUpperCase() : '?');

  if (!token) {
    return (
      <div className="auth-root">
        <section className="auth-card">
          <div className="auth-brand">
            <h1>ChatChat</h1>
            <p>Личные и групповые чаты в одном месте</p>
          </div>
          <div className="auth-tabs">
            <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
              Вход
            </button>
            <button
              type="button"
              className={mode === 'register' ? 'active' : ''}
              onClick={() => setMode('register')}
            >
              Регистрация
            </button>
          </div>
          <div className="field">
            <label htmlFor="auth-user">Логин</label>
            <input
              id="auth-user"
              value={loginState.username}
              onChange={e => setLoginState(prev => ({ ...prev, username: e.target.value }))}
              autoComplete="username"
            />
          </div>
          <div className="field">
            <label htmlFor="auth-pass">Пароль</label>
            <input
              id="auth-pass"
              type="password"
              value={loginState.password}
              onChange={e => setLoginState(prev => ({ ...prev, password: e.target.value }))}
              autoComplete="current-password"
            />
          </div>
          <button type="button" className="btn-primary" onClick={() => handleAuth(mode)}>
            {mode === 'login' ? 'Войти' : 'Создать аккаунт'}
          </button>
          {error ? <div className="alert-error">{error}</div> : null}
        </section>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-top">
          <div className="who">
            <strong title={username}>{username}</strong>
            <div className={`pill ${connected ? 'on' : 'off'}`}>
              <span className="pill-dot" aria-hidden />
              {connected ? 'онлайн' : 'офлайн'}
            </div>
          </div>
          <button type="button" className="btn-ghost" onClick={logout}>
            Выйти
          </button>
        </div>

        {invitations.length > 0 && (
          <div className="panel-block" style={{ background: '#fff3cd', border: '1px solid #ffc107' }}>
            <h3 style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Приглашения ({invitations.length})</span>
              <button
                type="button"
                className="btn-ghost"
                style={{ fontSize: '0.8rem', padding: '2px 8px' }}
                onClick={() => setShowInvitations(!showInvitations)}
              >
                {showInvitations ? 'Скрыть' : 'Показать'}
              </button>
            </h3>
            {showInvitations && (
              <div style={{ marginTop: '8px' }}>
                {invitations.map(inv => (
                  <div
                    key={inv.id}
                    style={{
                      padding: '8px',
                      marginBottom: '8px',
                      background: 'white',
                      borderRadius: '4px',
                      fontSize: '0.9rem'
                    }}
                  >
                    <div style={{ marginBottom: '4px' }}>
                      <strong>{inv.roomName}</strong>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#666', marginBottom: '8px' }}>
                      от {inv.invitedBy}
                    </div>
                    <div style={{ display: 'flex', gap: '4px' }}>
                      <button
                        type="button"
                        className="btn-small primary"
                        onClick={() => acceptInvitation(inv.id)}
                      >
                        Принять
                      </button>
                      <button
                        type="button"
                        className="btn-small"
                        onClick={() => declineInvitation(inv.id)}
                      >
                        Отклонить
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="panel-block">
          <h3>Личный чат</h3>
          <input
            placeholder="Имя пользователя"
            value={dmUsername}
            onChange={e => setDmUsername(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && openDirect()}
          />
          <div className="btn-row">
            <button type="button" className="btn-small primary" onClick={openDirect}>
              Открыть
            </button>
          </div>
        </div>

        <div className="panel-block">
          <h3>Новая группа</h3>
          <input
            placeholder="Название"
            value={newRoomName}
            onChange={e => setNewRoomName(e.target.value)}
          />
          <input
            placeholder="Добавить логины (через запятую)"
            value={userQuery}
            onChange={e => setUserQuery(e.target.value)}
          />
          {userSuggestions.length > 0 ? (
            <div className="suggestions">
              {userSuggestions.map(u => (
                <button key={u} type="button" onClick={() => pickSuggestion(u)}>
                  {u}
                </button>
              ))}
            </div>
          ) : null}
          <input
            placeholder="Участники: alice, bob"
            value={newRoomParticipants}
            onChange={e => setNewRoomParticipants(e.target.value)}
          />
          <div className="btn-row">
            <button type="button" className="btn-small primary" onClick={createRoom}>
              Создать группу
            </button>
          </div>
        </div>

        <div className="room-scroll">
          <h3>Диалоги</h3>
          {rooms.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: 0 }}>Пока пусто — начните с личного чата или группы.</p>
          ) : (
            rooms.map(room => (
              <button
                key={room.id}
                type="button"
                className={`room-item ${selectedRoom.id === room.id ? 'active' : ''}`}
                onClick={() => setSelectedRoom(room)}
              >
                <div className="avatar" aria-hidden>
                  {roomInitial(room.name)}
                </div>
                <div className="meta">
                  <div className="title">{room.name}</div>
                  <div className="sub">
                    {room.participants?.length ?? 0}{' '}
                    {room.participants?.length === 1 ? 'участник' : 'участников'}
                  </div>
                </div>
                <span className={`badge-type ${room.type === 'PRIVATE' ? 'private' : 'group'}`}>
                  {room.type === 'PRIVATE' ? 'личн.' : 'группа'}
                </span>
              </button>
            ))
          )}
        </div>
      </aside>

      <section className="chat-main">
        <header className="chat-header">
          <div>
            <h2>{selectedRoom.name || 'Выберите чат'}</h2>
            {selectedRoom.id ? (
              <p className="hint">
                {selectedRoom.type === 'PRIVATE' ? 'Личная переписка' : 'Групповой чат'}
                {selectedRoom.participants?.length ? ` · ${selectedRoom.participants.join(', ')}` : ''}
              </p>
            ) : (
              <p className="hint">Слева можно открыть диалог с человеком или создать группу.</p>
            )}
          </div>
          {selectedRoom.id && (
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
              <div style={{ fontSize: '0.85rem' }}>
                <label style={{ marginRight: '4px' }}>С:</label>
                <input
                  type="datetime-local"
                  value={dateFrom}
                  onChange={e => setDateFrom(e.target.value)}
                  style={{ fontSize: '0.85rem', padding: '2px 4px' }}
                />
              </div>
              <div style={{ fontSize: '0.85rem' }}>
                <label style={{ marginRight: '4px' }}>По:</label>
                <input
                  type="datetime-local"
                  value={dateTo}
                  onChange={e => setDateTo(e.target.value)}
                  style={{ fontSize: '0.85rem', padding: '2px 4px' }}
                />
              </div>
              {(dateFrom || dateTo) && (
                <button
                  type="button"
                  className="btn-ghost"
                  style={{ fontSize: '0.75rem', padding: '2px 8px' }}
                  onClick={() => {
                    setDateFrom('');
                    setDateTo('');
                  }}
                >
                  Сбросить
                </button>
              )}
              <button
                type="button"
                className="btn-ghost"
                style={{ fontSize: '0.85rem' }}
                onClick={() => setShowIpAddresses(!showIpAddresses)}
              >
                {showIpAddresses ? 'Скрыть IP' : 'Показать IP'}
              </button>
            </div>
          )}
          {selectedRoom.id && selectedRoom.type === 'GROUP' && selectedRoom.participants?.length > 0 && (
            <div style={{ fontSize: '0.85rem', marginTop: '8px' }}>
              <details>
                <summary style={{ cursor: 'pointer', userSelect: 'none' }}>Участники</summary>
                <div style={{ marginTop: '8px', maxHeight: '200px', overflowY: 'auto' }}>
                  {selectedRoom.participants.map(p => {
                    const isCreator = rooms.find(r => r.id === selectedRoom.id)?.creatorUsername === username;
                    return (
                      <div
                        key={p}
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          padding: '4px 0'
                        }}
                      >
                        <span>{p}</span>
                        {p !== username && isCreator && (
                          <button
                            type="button"
                            className="btn-ghost"
                            style={{ fontSize: '0.75rem', padding: '2px 6px', color: '#dc3545' }}
                            onClick={() => {
                              if (window.confirm(`Удалить ${p} из группы?`)) {
                                kickParticipant(p);
                              }
                            }}
                          >
                            Удалить
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              </details>
            </div>
          )}
        </header>

        {!selectedRoom.id ? (
          <div className="empty-chat">
            <p>Выберите чат в списке или создайте новый.</p>
          </div>
        ) : (
          <>
            <div className="message-list">
              {messages.map(message => {
                const mine = message.sender === username;
                const isSystem = message.isSystem === true;

                if (isSystem) {
                  return (
                    <div key={message.id ?? `${message.sender}-${message.sentAt}`} style={{ textAlign: 'center', margin: '12px 0' }}>
                      <div style={{
                        display: 'inline-block',
                        padding: '6px 12px',
                        background: '#e9ecef',
                        borderRadius: '12px',
                        fontSize: '0.85rem',
                        color: '#495057'
                      }}>
                        {message.content}
                      </div>
                    </div>
                  );
                }

                return (
                  <div key={message.id ?? `${message.sender}-${message.sentAt}`} className={`bubble-row ${mine ? 'mine' : ''}`}>
                    <div className="bubble">
                      {!mine ? <div className="who">{message.sender}</div> : null}
                      <p>{message.content}</p>
                      <div className="time">
                        {formatTime(message.sentAt)}
                        {showIpAddresses && message.senderIp && (
                          <span style={{ marginLeft: '8px', fontSize: '0.75rem', opacity: 0.7 }}>
                            · IP: {message.senderIp}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            <div className="composer">
              <textarea
                placeholder="Сообщение… Enter — отправить, Shift+Enter — новая строка"
                value={newMessage}
                onChange={e => setNewMessage(e.target.value)}
                rows={2}
                onKeyDown={e => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendMessage();
                  }
                }}
              />
              <button
                type="button"
                className="send-btn"
                onClick={sendMessage}
                disabled={!newMessage.trim()}
              >
                Отправить
              </button>
            </div>
          </>
        )}

        {error ? (
          <div className="footer-error">
            <div className="alert-error">{error}</div>
          </div>
        ) : null}
      </section>
    </div>
  );
}

export default App;
