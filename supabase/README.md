# Lmg Client config sharing (Supabase)

Short share codes (`LMG-ABC234`) are stored in the Supabase project **lmg-client**
(`fieakrewvbyubtyxpqhr`, us-east-1).

- `public.shared_configs` has row-level security on and **no policies**, so the table can't be
  read or written directly through the API.
- Clients can only call two functions (`/rest/v1/rpc/...`):
  - `share_config(p_name, p_data)`: stores an `LMG1:` code and returns a 6-character code.
    Validates the data, returns the existing code for identical configs, and limits each
    network address (stored as a salted hash) to 20 new shares per hour.
  - `get_config(p_code)`: returns `{name, data}` for a code (accepts `LMG-ABC234`, `abc234`...).
- The mod uses the project's **publishable** key (meant to be public).
- Free projects pause after ~1 week without use. If short codes stop working, un-pause the project
  in the Supabase dashboard. Long `LMG1:` codes always work offline.

The schema is in `migrations/`.
