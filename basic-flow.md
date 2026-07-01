Here's the full workflow for adding tables and performing operations during requests.

---

## Adding a New Table

Open `server/database/schema.ts` and add your table definition alongside the existing ones (user, session, account, items):

```typescript
export const tags = sqliteTable('tags', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  userId: text('userId').notNull().references(() => user.id),
  name: text('name').notNull(),
  createdAt: integer('createdAt', { mode: 'timestamp' }).notNull(),
})
```

Generate and apply the migration, same flow as before:

```bash
npx drizzle-kit generate
npx wrangler d1 migrations apply my-app-db --local
```

That's the entire process for adding a table once your schema file, drizzle config, and migrations dir are already wired up — no new setup needed, just repeat the generate → apply cycle every time you touch `schema.ts`.

---

## Creating a Reusable DB Helper

Before doing operations in routes, set up one helper so you're not repeating the binding-access boilerplate everywhere:

```typescript
// server/utils/db.ts
import { drizzle } from 'drizzle-orm/d1'
import * as schema from '../database/schema'

export function useDB(event: any) {
  const d1 = event.context.cloudflare.env.DB
  return drizzle(d1, { schema })
}

export const tables = schema
```

---

## Performing Operations in a Request

Inside any server route, call `useDB(event)` to get a typed Drizzle instance, then use it directly.

**Insert:**
```typescript
// server/api/save.post.ts
export default defineEventHandler(async (event) => {
  const db = useDB(event)
  const body = await readBody(event)

  const result = await db.insert(tables.items).values({
    userId: body.userId,
    type: body.type,
    url: body.url,
    title: body.title,
    summary: body.summary,
    tags: JSON.stringify(body.tags),
    createdAt: new Date(),
  }).returning()

  return result[0]
})
```

**Select with filter:**
```typescript
// server/api/items.get.ts
export default defineEventHandler(async (event) => {
  const db = useDB(event)
  const query = getQuery(event)

  const items = await db.query.items.findMany({
    where: (item, { eq }) => eq(item.userId, query.userId as string),
    orderBy: (item, { desc }) => desc(item.createdAt),
  })

  return items
})
```

**Update:**
```typescript
await db.update(tables.items)
  .set({ title: 'New title' })
  .where(eq(tables.items.id, itemId))
```

**Delete:**
```typescript
await db.delete(tables.items).where(eq(tables.items.id, itemId))
```

**Join across tables (e.g. items + tags):**
```typescript
const results = await db
  .select()
  .from(tables.items)
  .leftJoin(tables.tags, eq(tables.items.id, tables.tags.itemId))
  .where(eq(tables.items.userId, userId))
```

---

## Inside grammY Handlers (Telegram Bot)

The pattern is the same — `event` is available wherever your Nitro route runs, including inside the grammY webhook handler before `webhookCallback` is called:

```typescript
// server/api/telegram.post.ts
export default defineEventHandler(async (event) => {
  const db = useDB(event)
  const bot = new Bot(useRuntimeConfig().telegramBotToken)

  bot.on('message:text', async (ctx) => {
    const processed = await processContent(ctx.message.text, 'text')

    await db.insert(tables.items).values({
      userId: String(ctx.chat.id),
      type: 'text',
      title: processed.title,
      summary: processed.summary,
      tags: JSON.stringify(processed.tags),
      createdAt: new Date(),
    })

    await ctx.reply(`Saved! Title: ${processed.title}`)
  })

  const handler = webhookCallback(bot, 'cloudflare-mod')
  return handler(toWebRequest(event))
})
```

---

## Inside AI SDK Tool Calls

Same pattern again — `event` needs to be passed through to wherever the tool's `execute` function runs:

```typescript
tools: {
  filterByTags: tool({
    description: 'Filter saved items by tags',
    parameters: z.object({ tags: z.array(z.string()) }),
    execute: async ({ tags }) => {
      const db = useDB(event) // event captured from the outer route closure
      return await db.query.items.findMany({
        where: (item, { like, or }) => or(...tags.map(t => like(item.tags, `%${t}%`))),
      })
    },
  }),
}
```

This is exactly the scenario where `asyncContext: true` matters — the tool's `execute` function runs asynchronously, potentially several ticks after the route handler started, and without that flag the `event` context (and therefore the D1 binding) can become stale or inaccessible.

---

## Quick Mental Model

Every operation follows the same three-step shape: get `db` via `useDB(event)`, reference tables via `tables.tableName`, then call Drizzle's query builder methods (`insert`, `select`/`query.X.findMany`, `update`, `delete`). The binding is always pulled fresh from `event.context.cloudflare.env.DB` per request — never cached at module level, since Workers are stateless between invocations.