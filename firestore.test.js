const test = require('node:test');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

const PROJECT_ID = 'demo-no-project';
const DATABASE_ID = 'ai-studio-android-orbit-20e61bc5-0bbd-4bae-97e7-1e0a7e33cde7';

let testEnv;

function validTs() {
  return new Date(Date.now() - 1000);
}

test.before(async () => {
  const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8085').split(':');
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port: Number(portStr),
      rules: fs.readFileSync('firestore.rules', 'utf8'),
    },
  });
});

test.after(async () => {
  if (testEnv) await testEnv.cleanup();
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
});

function dbFor(uid) {
  if (!uid) return testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  return testEnv.authenticatedContext(uid).firestore(DATABASE_ID);
}

test('Unauthenticated users cannot read or write any collection', async () => {
  const unauth = dbFor(null);
  await assertFails(unauth.collection('posts').where('visibility', '==', 'public').get());
  await assertFails(unauth.collection('communities').where('visibility', '==', 'public').get());
  await assertFails(unauth.collection('profiles').doc('alice').get());
  await assertFails(unauth.collection('conversations').doc('dm_alice_bob').get());
  await assertFails(unauth.collection('appReleases').doc('latest').get());
});

test('Profiles: owner create, savedLinks update, legacy migration, and cross-user isolation', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');
  const createdTs = validTs();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore(DATABASE_ID);
    await adminDb.collection('profiles').doc('alice').set({
      handle: 'old_alice',
      displayName: 'Old Alice',
      isPrivate: false,
    });
  });

  await assertSucceeds(aliceDb.collection('profiles').doc('alice').get());
  await assertSucceeds(aliceDb.collection('profiles').doc('alice').set({
    userId: 'alice',
    username: 'alice',
    displayName: 'Alice Orbit',
    bio: 'Photographer',
    avatarUrl: '',
    bannerUrl: '',
    linkUrl: 'https://orbit.app',
    savedLinks: [],
    visibility: 'public',
    followerCount: 0,
    followingCount: 0,
    createdAt: createdTs,
    updatedAt: validTs(),
  }));

  // Owner updates savedLinks and bannerUrl
  await assertSucceeds(aliceDb.collection('profiles').doc('alice').update({
    bio: 'Updated bio',
    bannerUrl: 'data:image/jpeg;base64,/9j/4AAQ',
    savedLinks: [{
      id: 'link_1',
      title: 'My Portfolio',
      url: 'https://example.com/portfolio',
      thumbnailUrl: 'https://www.google.com/s2/favicons?sz=128&domain=example.com',
      createdAtMillis: Date.now(),
    }],
    updatedAt: validTs(),
  }));

  // Bob cannot edit Alice's savedLinks, but CAN update followerCount
  await assertFails(bobDb.collection('profiles').doc('alice').update({
    savedLinks: [],
    updatedAt: validTs(),
  }));
  await assertSucceeds(bobDb.collection('profiles').doc('alice').update({
    followerCount: 1,
    updatedAt: validTs(),
  }));
});

test('Posts: quote repost, author edit with edited flag, and cross-user edit denial', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');

  await assertSucceeds(aliceDb.collection('posts').doc('post_1').set({
    authorId: 'alice',
    authorUsername: 'alice',
    authorDisplayName: 'Alice',
    authorAvatarUrl: '',
    body: 'Hello Orbit!',
    topic: '',
    imageUrls: [],
    attachments: [],
    parentPostId: '',
    rootPostId: '',
    repostOfPostId: '',
    repostAuthorUsername: '',
    quotedPostId: 'post_0',
    quotedAuthorUsername: 'bob',
    quotedAuthorDisplayName: 'Bob',
    quotedAuthorAvatarUrl: '',
    quotedBody: 'Original thought',
    quotedImageUrl: '',
    edited: false,
    visibility: 'public',
    likeCount: 0,
    replyCount: 0,
    repostCount: 0,
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  // Author can edit body & edited flag
  await assertSucceeds(aliceDb.collection('posts').doc('post_1').update({
    body: 'Hello Orbit (edited)!',
    edited: true,
    updatedAt: validTs(),
  }));

  // Bob cannot edit Alice's post body
  await assertFails(bobDb.collection('posts').doc('post_1').update({
    body: 'Hijacked',
    edited: true,
    updatedAt: validTs(),
  }));

  // Bob can increment likeCount
  await assertSucceeds(bobDb.collection('posts').doc('post_1').update({
    likeCount: 1,
    updatedAt: validTs(),
  }));
});

test('Orbit Spaces RBAC: Admin vs Moderator vs Member permissions & Space Post deletion', async () => {
  const aliceDb = dbFor('alice'); // Admin
  const bobDb = dbFor('bob');     // Moderator
  const carolDb = dbFor('carol'); // Regular Member

  await assertSucceeds(aliceDb.collection('communities').doc('space_1').set({
    name: 'Pixel Photographers',
    description: 'Share shots',
    category: 'Photography',
    coverUrl: '',
    accentIndex: 1,
    ownerId: 'alice',
    ownerUsername: 'alice',
    privacyMode: 'private',
    moderatorIds: [],
    bannedUserIds: [],
    mutedUntilMap: {},
    pendingRequestIds: [],
    invitedUserIds: [],
    memberIds: ['alice'],
    memberCount: 1,
    postCount: 0,
    visibility: 'public',
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  // Carol requests to join private Space
  await assertSucceeds(carolDb.collection('communities').doc('space_1').update({
    pendingRequestIds: ['carol'],
    updatedAt: validTs(),
  }));

  // Admin promotes Bob to moderator and approves Carol
  await assertSucceeds(aliceDb.collection('communities').doc('space_1').update({
    moderatorIds: ['bob'],
    memberIds: ['alice', 'bob', 'carol'],
    memberCount: 3,
    pendingRequestIds: [],
    updatedAt: validTs(),
  }));

  // Moderator Bob CAN mute/ban/kick members
  await assertSucceeds(bobDb.collection('communities').doc('space_1').update({
    mutedUntilMap: { carol: Date.now() + 3600000 },
    updatedAt: validTs(),
  }));

  // Moderator Bob CANNOT rename Space, edit About/banner, change privacy, or delete Space
  await assertFails(bobDb.collection('communities').doc('space_1').update({
    name: 'Bob Renamed It',
    updatedAt: validTs(),
  }));
  await assertFails(bobDb.collection('communities').doc('space_1').update({
    description: 'Bob edited about',
    updatedAt: validTs(),
  }));
  await assertFails(bobDb.collection('communities').doc('space_1').update({
    coverUrl: 'data:image/jpeg;base64,/9j/',
    updatedAt: validTs(),
  }));
  await assertFails(bobDb.collection('communities').doc('space_1').delete());

  // Carol posts inside the Space
  await assertSucceeds(carolDb.collection('communityPosts').doc('spost_1').set({
    communityId: 'space_1',
    authorId: 'carol',
    authorUsername: 'carol',
    authorDisplayName: 'Carol',
    authorAvatarUrl: '',
    streamCategory: 'Showcase',
    body: 'My golden hour photo',
    imageUrls: [],
    attachments: [],
    edited: false,
    plusOneCount: 0,
    plusOneUserIds: [],
    commentCount: 0,
    visibility: 'public',
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  // Carol can edit her own Space post
  await assertSucceeds(carolDb.collection('communityPosts').doc('spost_1').update({
    body: 'My golden hour photo (updated)',
    edited: true,
    updatedAt: validTs(),
  }));

  // Moderator Bob CAN delete Carol's Space post
  await assertSucceeds(bobDb.collection('communityPosts').doc('spost_1').delete());

  // Admin Alice CAN delete the Space
  await assertSucceeds(aliceDb.collection('communities').doc('space_1').delete());
});

test('Activities & AppReleases: Space notifications and update notices succeed', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');

  await assertSucceeds(aliceDb.collection('activities').doc('act_1').set({
    recipientId: 'bob',
    actorId: 'alice',
    actorUsername: 'alice',
    actorAvatarUrl: '',
    type: 'space_invite',
    postId: '',
    spaceId: 'space_1',
    snippet: 'Invited you to join Pixel Photographers',
    createdAt: validTs(),
  }));

  await assertSucceeds(bobDb.collection('activities').where('recipientId', '==', 'bob').get());
  await assertFails(aliceDb.collection('activities').where('recipientId', '==', 'bob').get());

  await assertSucceeds(aliceDb.collection('appReleases').doc('latest').set({
    versionCode: 10,
    versionName: '9.1.1',
    releaseNotes: 'Spaces join fix, Media Albums, Privacy controls, App Fonts, and Drive APK download!',
    driveDownloadUrl: 'https://drive.google.com/file/d/abc123XYZ/view?usp=sharing',
    publishedBy: 'alice',
    updatedAt: validTs(),
  }));
  await assertSucceeds(bobDb.collection('appReleases').doc('latest').get());
});

test('Missing documents allow safe get (resource == null) without PERMISSION_DENIED', async () => {
  const aliceDb = dbFor('alice');
  await assertSucceeds(aliceDb.collection('communities').doc('nonexistent_space').get());
  await assertSucceeds(aliceDb.collection('posts').doc('nonexistent_post').get());
  await assertSucceeds(aliceDb.collection('conversations').doc('nonexistent_conv').get());
  await assertSucceeds(aliceDb.collection('profiles').doc('nonexistent_user').get());
});

test('Profile Media Albums & Items: owner CRUD, reorder, cover update, and cross-user denial', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');
  const createdTs = validTs();

  // Owner creates album
  await assertSucceeds(aliceDb.collection('profiles').doc('alice').collection('albums').doc('album_1').set({
    ownerId: 'alice',
    ownerUsername: 'alice',
    title: 'Summer Shots',
    coverUrl: '',
    itemCount: 0,
    visibility: 'public',
    createdAt: createdTs,
    updatedAt: validTs(),
  }));

  // Owner adds item
  await assertSucceeds(
    aliceDb.collection('profiles').doc('alice').collection('albums').doc('album_1').collection('items').doc('item_1').set({
      ownerId: 'alice',
      mediaUrl: 'data:image/jpeg;base64,/9j/4AAQ',
      mimeType: 'image/jpeg',
      mediaType: 'image',
      fileName: 'sunset.jpg',
      sortOrder: 0,
      visibility: 'public',
      createdAt: validTs(),
    })
  );

  // Owner updates sortOrder and coverUrl
  await assertSucceeds(
    aliceDb.collection('profiles').doc('alice').collection('albums').doc('album_1').collection('items').doc('item_1').update({
      sortOrder: 2,
    })
  );
  await assertSucceeds(
    aliceDb.collection('profiles').doc('alice').collection('albums').doc('album_1').update({
      coverUrl: 'data:image/jpeg;base64,/9j/4AAQ',
      itemCount: 1,
      updatedAt: validTs(),
    })
  );

  // Bob can read Alice's public album & items, but CANNOT modify or delete them
  await assertSucceeds(bobDb.collection('profiles').doc('alice').collection('albums').doc('album_1').get());
  await assertSucceeds(bobDb.collection('profiles').doc('alice').collection('albums').doc('album_1').collection('items').doc('item_1').get());
  await assertFails(bobDb.collection('profiles').doc('alice').collection('albums').doc('album_1').update({
    title: 'Hacked',
    updatedAt: validTs(),
  }));
  await assertFails(bobDb.collection('profiles').doc('alice').collection('albums').doc('album_1').delete());
  await assertFails(bobDb.collection('profiles').doc('alice').collection('albums').doc('album_1').collection('items').doc('item_1').delete());

  // Owner deletes item and album
  await assertSucceeds(aliceDb.collection('profiles').doc('alice').collection('albums').doc('album_1').collection('items').doc('item_1').delete());
  await assertSucceeds(aliceDb.collection('profiles').doc('alice').collection('albums').doc('album_1').delete());
});

test('Private user state (Saved Posts, Blocked/Muted Users, Muted Words) & Post Privacy Controls', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');

  await assertSucceeds(aliceDb.collection('users').doc('alice').collection('private').doc('state').set({
    userId: 'alice',
    email: 'alice@example.com',
    followingIds: ['bob'],
    likedPostIds: [],
    repostedPostIds: [],
    savedPostIds: ['post_1'],
    blockedUserIds: ['troll_1'],
    mutedUserIds: ['noisy_1'],
    mutedWords: ['spoilers'],
    updatedAt: validTs(),
  }));

  // Bob cannot read or write Alice's private state
  await assertFails(bobDb.collection('users').doc('alice').collection('private').doc('state').get());
  await assertFails(bobDb.collection('users').doc('alice').collection('private').doc('state').update({
    savedPostIds: [],
    updatedAt: validTs(),
  }));

  // Post with followers_only audience & selected_users reply restriction
  await assertSucceeds(aliceDb.collection('posts').doc('post_priv').set({
    authorId: 'alice',
    authorUsername: 'alice',
    authorDisplayName: 'Alice',
    authorAvatarUrl: '',
    body: 'Followers only update',
    topic: '',
    imageUrls: [],
    attachments: [],
    parentPostId: '',
    rootPostId: '',
    repostOfPostId: '',
    repostAuthorUsername: '',
    quotedPostId: '',
    quotedAuthorUsername: '',
    quotedAuthorDisplayName: '',
    quotedAuthorAvatarUrl: '',
    quotedBody: '',
    quotedImageUrl: '',
    audienceMode: 'followers_only',
    replyMode: 'selected_users',
    allowedReplyUserIds: ['bob'],
    edited: false,
    visibility: 'public',
    likeCount: 0,
    replyCount: 0,
    repostCount: 0,
    createdAt: validTs(),
    updatedAt: validTs(),
  }));
});

test('Direct Messages: participant chat, sender-only message delete, and conversation delete', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');
  const carolDb = dbFor('carol');

  await assertSucceeds(aliceDb.collection('conversations').doc('dm_alice_bob').set({
    participantIds: ['alice', 'bob'],
    participantNames: { alice: 'Alice', bob: 'Bob' },
    participantUsernames: { alice: 'alice', bob: 'bob' },
    participantAvatars: { alice: '', bob: '' },
    lastMessageText: 'Hey Bob',
    lastMessageSenderId: 'alice',
    lastMessageAt: validTs(),
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  await assertSucceeds(aliceDb.collection('conversations').doc('dm_alice_bob').collection('messages').doc('m1').set({
    senderId: 'alice',
    senderUsername: 'alice',
    body: 'Hey Bob',
    imageUrl: '',
    attachments: [],
    createdAt: validTs(),
  }));

  // Outsider Carol cannot read or delete
  await assertFails(carolDb.collection('conversations').doc('dm_alice_bob').get());
  await assertFails(carolDb.collection('conversations').doc('dm_alice_bob').delete());

  // Bob cannot delete Alice's message, but Alice can delete her own message
  await assertFails(bobDb.collection('conversations').doc('dm_alice_bob').collection('messages').doc('m1').delete());
  await assertSucceeds(aliceDb.collection('conversations').doc('dm_alice_bob').collection('messages').doc('m1').delete());

  // Participant Bob can delete the conversation
  await assertSucceeds(bobDb.collection('conversations').doc('dm_alice_bob').delete());
});

test('DM Read Receipts: participants can only update their own lastReadAtByUser timestamp', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');
  const carolDb = dbFor('carol');

  await assertSucceeds(aliceDb.collection('conversations').doc('dm_alice_bob').set({
    participantIds: ['alice', 'bob'],
    participantNames: { alice: 'Alice', bob: 'Bob' },
    participantUsernames: { alice: 'alice', bob: 'bob' },
    participantAvatars: { alice: '', bob: '' },
    lastReadAtByUser: { alice: validTs() },
    lastMessageText: 'Hello!',
    lastMessageSenderId: 'alice',
    lastMessageAt: validTs(),
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  // Bob marks conversation as seen (updates ONLY lastReadAtByUser.bob)
  await assertSucceeds(bobDb.collection('conversations').doc('dm_alice_bob').update({
    'lastReadAtByUser.bob': validTs(),
    updatedAt: validTs(),
  }));

  // Bob CANNOT forge or overwrite Alice's read receipt timestamp
  await assertFails(bobDb.collection('conversations').doc('dm_alice_bob').update({
    'lastReadAtByUser.alice': validTs(),
    updatedAt: validTs(),
  }));

  // Outsider Carol CANNOT mark the conversation read
  await assertFails(carolDb.collection('conversations').doc('dm_alice_bob').update({
    'lastReadAtByUser.carol': validTs(),
    updatedAt: validTs(),
  }));
});

test('Group Direct Messages: multi-participant creation, messaging, read receipts, and outsider denial', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');
  const carolDb = dbFor('carol');
  const daveDb = dbFor('dave');

  // Alice creates a 3-person group DM with Bob and Carol
  await assertSucceeds(aliceDb.collection('conversations').doc('grp_design_crew').set({
    participantIds: ['alice', 'bob', 'carol'],
    participantNames: { alice: 'Alice', bob: 'Bob', carol: 'Carol' },
    participantUsernames: { alice: 'alice', bob: 'bob', carol: 'carol' },
    participantAvatars: { alice: '', bob: '', carol: '' },
    groupTitle: 'Design Crew',
    lastReadAtByUser: { alice: validTs() },
    lastMessageText: '',
    lastMessageSenderId: '',
    lastMessageAt: validTs(),
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  // Bob and Carol can read and send messages into the group thread
  await assertSucceeds(bobDb.collection('conversations').doc('grp_design_crew').get());
  await assertSucceeds(carolDb.collection('conversations').doc('grp_design_crew').collection('messages').doc('gm1').set({
    senderId: 'carol',
    senderUsername: 'carol',
    body: 'Welcome everyone!',
    imageUrl: '',
    attachments: [],
    createdAt: validTs(),
  }));

  // Bob and Carol can each update only their own lastReadAtByUser timestamp
  await assertSucceeds(bobDb.collection('conversations').doc('grp_design_crew').update({
    'lastReadAtByUser.bob': validTs(),
    updatedAt: validTs(),
  }));
  await assertSucceeds(carolDb.collection('conversations').doc('grp_design_crew').update({
    'lastReadAtByUser.carol': validTs(),
    updatedAt: validTs(),
  }));

  // Bob cannot overwrite Carol's read receipt
  await assertFails(bobDb.collection('conversations').doc('grp_design_crew').update({
    'lastReadAtByUser.carol': validTs(),
    updatedAt: validTs(),
  }));

  // Outsider Dave cannot read, message, or update the group DM
  await assertFails(daveDb.collection('conversations').doc('grp_design_crew').get());
  await assertFails(daveDb.collection('conversations').doc('grp_design_crew').collection('messages').doc('gm2').set({
    senderId: 'dave',
    senderUsername: 'dave',
    body: 'Let me in',
    imageUrl: '',
    attachments: [],
    createdAt: validTs(),
  }));

  // Creating a group with > 12 participants is rejected
  const tooMany = ['alice', 'u2', 'u3', 'u4', 'u5', 'u6', 'u7', 'u8', 'u9', 'u10', 'u11', 'u12', 'u13'];
  await assertFails(aliceDb.collection('conversations').doc('grp_too_large').set({
    participantIds: tooMany,
    groupTitle: 'Too Large',
    lastReadAtByUser: { alice: validTs() },
    lastMessageText: '',
    lastMessageSenderId: '',
    lastMessageAt: validTs(),
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  // Group title exceeding 60 characters is rejected
  await assertFails(aliceDb.collection('conversations').doc('grp_long_title').set({
    participantIds: ['alice', 'bob', 'carol'],
    groupTitle: 'A'.repeat(61),
    lastReadAtByUser: { alice: validTs() },
    lastMessageText: '',
    lastMessageSenderId: '',
    lastMessageAt: validTs(),
    createdAt: validTs(),
    updatedAt: validTs(),
  }));
});

test('Polls: creation, zero-trust per-user voting, vote switching, and tampering denial', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');

  const basePollPost = {
    authorId: 'alice',
    authorUsername: 'alice',
    authorDisplayName: 'Alice',
    authorAvatarUrl: '',
    body: 'Best coffee brew method?',
    topic: '',
    imageUrls: [],
    attachments: [],
    parentPostId: '',
    rootPostId: '',
    repostOfPostId: '',
    repostAuthorUsername: '',
    quotedPostId: '',
    quotedAuthorUsername: '',
    quotedAuthorDisplayName: '',
    quotedAuthorAvatarUrl: '',
    quotedBody: '',
    quotedImageUrl: '',
    audienceMode: 'everyone',
    replyMode: 'everyone',
    allowedReplyUserIds: [],
    pollOptions: ['Pour-over', 'Espresso', 'Cold brew'],
    pollVotes: {},
    pollEndsAtMillis: Date.now() + 86400000,
    edited: false,
    visibility: 'public',
    likeCount: 0,
    replyCount: 0,
    repostCount: 0,
    createdAt: validTs(),
    updatedAt: validTs(),
  };

  // Creating a post with pre-stuffed votes fails
  await assertFails(aliceDb.collection('posts').doc('post_poll_stuffed').set({
    ...basePollPost,
    pollVotes: { alice: 0 },
  }));

  // Creating a valid poll post succeeds
  await assertSucceeds(aliceDb.collection('posts').doc('post_poll_1').set(basePollPost));

  // Bob casts his own vote on option 1
  await assertSucceeds(bobDb.collection('posts').doc('post_poll_1').update({
    'pollVotes.bob': 1,
    updatedAt: validTs(),
  }));

  // Bob switches his own vote to option 2
  await assertSucceeds(bobDb.collection('posts').doc('post_poll_1').update({
    'pollVotes.bob': 2,
    updatedAt: validTs(),
  }));

  // Bob CANNOT cast an out-of-bounds option index
  await assertFails(bobDb.collection('posts').doc('post_poll_1').update({
    'pollVotes.bob': 3,
    updatedAt: validTs(),
  }));

  // Bob CANNOT forge or modify Alice's vote
  await assertFails(bobDb.collection('posts').doc('post_poll_1').update({
    'pollVotes.alice': 0,
    updatedAt: validTs(),
  }));

  // Bob CANNOT alter pollOptions
  await assertFails(bobDb.collection('posts').doc('post_poll_1').update({
    pollOptions: ['Hacked', 'Option'],
    updatedAt: validTs(),
  }));
});

test('Mention Notifications: mentioning another user succeeds, self-mention and impersonation fail', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');

  // Alice mentions Bob in a post
  await assertSucceeds(aliceDb.collection('activities').doc('act_mention_1').set({
    recipientId: 'bob',
    actorId: 'alice',
    actorUsername: 'alice',
    actorAvatarUrl: '',
    type: 'mention',
    postId: 'post_123',
    spaceId: '',
    snippet: 'Hey @bob check out this shot!',
    createdAt: validTs(),
  }));

  // Bob can read his own mention notification
  await assertSucceeds(bobDb.collection('activities').doc('act_mention_1').get());

  // Alice cannot self-mention
  await assertFails(aliceDb.collection('activities').doc('act_mention_self').set({
    recipientId: 'alice',
    actorId: 'alice',
    actorUsername: 'alice',
    actorAvatarUrl: '',
    type: 'mention',
    postId: 'post_123',
    spaceId: '',
    snippet: 'Hey @alice',
    createdAt: validTs(),
  }));

  // Alice cannot forge a mention claiming Bob was the actor
  await assertFails(aliceDb.collection('activities').doc('act_mention_forge').set({
    recipientId: 'carol',
    actorId: 'bob',
    actorUsername: 'bob',
    actorAvatarUrl: '',
    type: 'mention',
    postId: 'post_123',
    spaceId: '',
    snippet: 'Forged mention',
    createdAt: validTs(),
  }));
});

test('Direct Message Emoji Reactions: participants can react/update their own reaction, non-participants and cross-user tampering fail', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');
  const eveDb = dbFor('eve');
  const convId = 'dm_alice_bob';

  await assertSucceeds(aliceDb.collection('conversations').doc(convId).set({
    participantIds: ['alice', 'bob'],
    participantNames: { alice: 'Alice', bob: 'Bob' },
    participantUsernames: { alice: 'alice', bob: 'bob' },
    participantAvatars: { alice: '', bob: '' },
    lastReadAtByUser: { alice: validTs() },
    lastMessageText: 'Hey Bob!',
    lastMessageSenderId: 'alice',
    lastMessageAt: validTs(),
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  const msgRefAlice = aliceDb.collection('conversations').doc(convId).collection('messages').doc('msg_react_1');
  const msgRefBob = bobDb.collection('conversations').doc(convId).collection('messages').doc('msg_react_1');
  const msgRefEve = eveDb.collection('conversations').doc(convId).collection('messages').doc('msg_react_1');

  await assertSucceeds(msgRefAlice.set({
    senderId: 'alice',
    senderUsername: 'alice',
    body: 'Check out this new message!',
    imageUrl: '',
    attachments: [],
    createdAt: validTs(),
  }));

  // Bob reacts with heart emoji
  await assertSucceeds(msgRefBob.update({
    'reactions.bob': '❤️',
  }));

  // Alice also reacts with fire emoji
  await assertSucceeds(msgRefAlice.update({
    'reactions.alice': '🔥',
  }));

  // Bob changes his reaction to laughing emoji
  await assertSucceeds(msgRefBob.update({
    'reactions.bob': '😂',
  }));

  // Bob CANNOT modify Alice's reaction entry
  await assertFails(msgRefBob.update({
    'reactions.alice': '👍',
  }));

  // Bob CANNOT alter message body while reacting
  await assertFails(msgRefBob.update({
    'reactions.bob': '👍',
    body: 'Tampered body',
  }));

  // Non-participant Eve CANNOT react to messages in the conversation
  await assertFails(msgRefEve.update({
    'reactions.eve': '❤️',
  }));
});

test('Profiles extraLinks (max 3) and reply context metadata rules', async () => {
  const aliceDb = dbFor('alice');
  const bobDb = dbFor('bob');

  await assertSucceeds(aliceDb.collection('profiles').doc('alice').set({
    userId: 'alice',
    username: 'alice',
    displayName: 'Alice Orbit',
    bio: 'Creator',
    avatarUrl: '',
    bannerUrl: '',
    linkUrl: 'https://alice.dev',
    extraLinks: ['https://instagram.com/alice', 'https://youtube.com/@alice', 'https://shop.alice.dev'],
    savedLinks: [],
    visibility: 'public',
    followerCount: 0,
    followingCount: 0,
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  // 4 extra links must fail validation
  await assertFails(aliceDb.collection('profiles').doc('alice').update({
    extraLinks: ['https://one.dev', 'https://two.dev', 'https://three.dev', 'https://four.dev'],
    updatedAt: validTs(),
  }));

  // Bob cannot modify Alice's extraLinks
  await assertFails(bobDb.collection('profiles').doc('alice').update({
    extraLinks: ['https://evil.dev'],
    updatedAt: validTs(),
  }));

  // Create root post + reply with replyToAuthorUsername and replyToBody
  await assertSucceeds(aliceDb.collection('posts').doc('root_ctx_1').set({
    authorId: 'alice',
    authorUsername: 'alice',
    authorDisplayName: 'Alice',
    body: 'First sunset photo from the coast!',
    parentPostId: '',
    rootPostId: '',
    visibility: 'public',
    likeCount: 0,
    replyCount: 0,
    repostCount: 0,
    createdAt: validTs(),
    updatedAt: validTs(),
  }));

  await assertSucceeds(bobDb.collection('posts').doc('reply_ctx_1').set({
    authorId: 'bob',
    authorUsername: 'bob',
    authorDisplayName: 'Bob',
    body: '@alice Amazing colors!',
    parentPostId: 'root_ctx_1',
    rootPostId: 'root_ctx_1',
    replyToAuthorUsername: 'alice',
    replyToBody: 'First sunset photo from the coast!',
    visibility: 'public',
    likeCount: 0,
    replyCount: 0,
    repostCount: 0,
    createdAt: validTs(),
    updatedAt: validTs(),
  }));
});







