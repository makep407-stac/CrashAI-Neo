"""
Application Streamlit temps réel - Monitoring 1xBet Crash
Affiche les crashs en temps réel avec statistiques et graphiques
"""
import streamlit as st
import pandas as pd
import plotly.graph_objects as go
import plotly.express as px
from datetime import datetime
import time
import os
import psycopg2
from psycopg2.extras import RealDictCursor

# Configuration
st.set_page_config(
    page_title="🎰 1xBet Crash Monitor",
    page_icon="🎲",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Style CSS
st.markdown("""
<style>
    .main-header {
        font-size: 2.5rem;
        font-weight: bold;
        text-align: center;
        background: linear-gradient(90deg, #FF6B6B, #4ECDC4);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        margin-bottom: 1rem;
    }
    .stMetric {
        background: white;
        padding: 1rem;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
</style>
""", unsafe_allow_html=True)

def get_db_connection():
    """Connect using explicit environment configuration; never use a default password."""
    database_url = os.getenv("DATABASE_URL", "").strip()
    if database_url:
        return psycopg2.connect(database_url)
    required = ("DATABASE_HOST", "DATABASE_PORT", "DATABASE_NAME", "DATABASE_USER", "DATABASE_PASSWORD")
    missing = [name for name in required if not os.getenv(name)]
    if missing:
        raise RuntimeError("Missing database environment configuration: " + ", ".join(missing))
    return psycopg2.connect(
        host=os.environ["DATABASE_HOST"],
        port=os.environ["DATABASE_PORT"],
        database=os.environ["DATABASE_NAME"],
        user=os.environ["DATABASE_USER"],
        password=os.environ["DATABASE_PASSWORD"],
    )

def get_recent_crashes(limit=100):
    """Récupère les crashs récents"""
    conn = None
    try:
        conn = get_db_connection()
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("""
                SELECT game_id, multiplier::float as multiplier, timestamp
                FROM crash_games
                ORDER BY timestamp DESC
                LIMIT %s
            """, (limit,))
            result = cur.fetchall()
            conn.commit()
            return result
    finally:
        if conn:
            conn.close()

def get_stats(hours=24):
    """Statistiques des dernières heures"""
    conn = None
    try:
        conn = get_db_connection()
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("""
                SELECT 
                    COUNT(*) as total,
                    AVG(multiplier) as avg_multiplier,
                    STDDEV(multiplier) as std_multiplier,
                    MIN(multiplier) as min_multiplier,
                    MAX(multiplier) as max_multiplier,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY multiplier) as median,
                    SUM(CASE WHEN multiplier < 2 THEN 1 ELSE 0 END) as low_count,
                    SUM(CASE WHEN multiplier >= 2 AND multiplier < 10 THEN 1 ELSE 0 END) as medium_count,
                    SUM(CASE WHEN multiplier >= 10 THEN 1 ELSE 0 END) as high_count
                FROM crash_games
                WHERE timestamp >= NOW() - INTERVAL '%s hours'
            """, (hours,))
            result = cur.fetchone()
            conn.commit()
            return result
    finally:
        if conn:
            conn.close()

def get_last_crash():
    """Dernier crash enregistré"""
    conn = None
    try:
        conn = get_db_connection()
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute("""
                SELECT game_id, multiplier, timestamp
                FROM crash_games
                ORDER BY timestamp DESC
                LIMIT 1
            """)
            result = cur.fetchone()
            conn.commit()
            return result
    finally:
        if conn:
            conn.close()

# Header
st.markdown('<h1 class="main-header">🎰 1xBet Crash - Moniteur Temps Réel</h1>', unsafe_allow_html=True)

# Sidebar — display controls only; no token or session handling.
with st.sidebar:
    st.header("⚙️ Configuration")
    auto_refresh = st.toggle("🔄 Auto-refresh", value=True)
    refresh_interval = st.slider("Intervalle (secondes)", 1, 10, 3)
    st.divider()
    st.header("📊 Période d'analyse")
    time_range = st.selectbox("Dernières heures", [1, 6, 12, 24, 48], index=3)
    display_limit = st.selectbox("Crashs à afficher", [50, 100, 200, 500], index=1)

# Récupérer les données
last_crash = None
recent_crashes = []
stats = None

try:
    last_crash = get_last_crash()
    stats = get_stats(time_range)
    recent_crashes = get_recent_crashes(display_limit)
    
    if not last_crash:
        st.warning("⏳ En attente des premières données...")
        st.info("Le scraper collecte les crashs en temps réel. Patientez quelques secondes...")
        time.sleep(5)
        st.rerun()
    
    # KPIs en haut
    col1, col2, col3, col4, col5 = st.columns(5)
    
    with col1:
        st.metric(
            "🎲 Dernier Crash",
            f"{last_crash['multiplier']:.2f}x",
            delta=f"#{last_crash['game_id']}"
        )
    
    with col2:
        st.metric(
            "📊 Total Crashs",
            f"{stats['total']:,}",
            delta=f"{time_range}h"
        )
    
    with col3:
        st.metric(
            "📈 Moyenne",
            f"{stats['avg_multiplier']:.2f}x",
            delta=f"±{stats['std_multiplier']:.2f}"
        )
    
    with col4:
        st.metric(
            "🔻 Min / Max",
            f"{stats['min_multiplier']:.2f}x",
            delta=f"Max: {stats['max_multiplier']:.0f}x"
        )
    
    with col5:
        seconds_ago = (datetime.now() - last_crash['timestamp']).total_seconds()
        st.metric(
            "⏱️ Dernier crash",
            f"{int(seconds_ago)}s",
            delta="Il y a"
        )
    
    st.divider()
    
    # Layout principal
    tab1, tab2, tab3 = st.tabs(["📈 Graphiques", "🔥 Analyse Avancée", "📋 Données"])
    
    with tab1:
        # Préparer le dataframe
        df = pd.DataFrame(recent_crashes)
        df['index'] = range(len(df), 0, -1)
        
        # Graphique principal - Full width
        st.subheader("📉 Historique des crashs")
        
        fig = go.Figure()
        
        # Ligne principale
        fig.add_trace(go.Scatter(
            x=df['index'],
            y=df['multiplier'],
            mode='lines+markers',
            name='Crashs',
            line=dict(color='#667eea', width=2),
            marker=dict(size=8, color=df['multiplier'], colorscale='Turbo', 
                       showscale=True, colorbar=dict(title="Mult."))
        ))
        
        # Moyenne mobile
        if len(df) >= 20:
            df['ma_20'] = df['multiplier'].rolling(window=20).mean()
            fig.add_trace(go.Scatter(
                x=df['index'],
                y=df['ma_20'],
                mode='lines',
                name='MA(20)',
                line=dict(color='#f7b731', width=2, dash='dash')
            ))
        
        # Ligne de seuil 2x
        fig.add_hline(y=2.0, line_dash="dot", line_color="red", 
                     annotation_text="Seuil 2x", annotation_position="right")
        
        fig.update_layout(
            xaxis_title="Parties (récent → ancien)",
            yaxis_title="Multiplicateur",
            height=450,
            template="plotly_white",
            hovermode='x unified',
            showlegend=True
        )
        
        st.plotly_chart(fig, use_container_width=True)
        
        # Deuxième ligne - 3 colonnes
        col1, col2, col3 = st.columns(3)
        
        with col1:
            st.subheader("🎯 Distribution")
            
            dist_data = pd.DataFrame({
                'Catégorie': ['< 2x', '2-10x', '> 10x'],
                'Count': [stats['low_count'], stats['medium_count'], stats['high_count']],
            })
            
            fig_pie = px.pie(
                dist_data,
                values='Count',
                names='Catégorie',
                color='Catégorie',
                color_discrete_map={'< 2x': '#FF6B6B', '2-10x': '#FFA500', '> 10x': '#4ECDC4'},
                hole=0.4
            )
            
            fig_pie.update_traces(textposition='inside', textinfo='percent+label')
            fig_pie.update_layout(height=300, showlegend=False)
            
            st.plotly_chart(fig_pie, use_container_width=True)
        
        with col2:
            st.subheader("📊 Histogramme")
            
            fig_hist = go.Figure(data=[go.Histogram(
                x=df['multiplier'],
                nbinsx=30,
                marker_color='#667eea',
                opacity=0.7
            )])
            
            fig_hist.update_layout(
                xaxis_title="Multiplicateur",
                yaxis_title="Fréquence",
                height=300,
                template="plotly_white",
                showlegend=False
            )
            
            st.plotly_chart(fig_hist, use_container_width=True)
        
        with col3:
            st.subheader("🏆 Top 10")
            top_10 = df.nlargest(10, 'multiplier')[['game_id', 'multiplier']].head(10)
            
            for idx, row in top_10.iterrows():
                emoji = "🔥" if row['multiplier'] > 10 else "⭐" if row['multiplier'] > 5 else "🎲"
                st.markdown(f"{emoji} **{row['multiplier']:.2f}x** - `#{row['game_id']}`")
    
    with tab2:
        st.subheader("🔥 Analyse Avancée")
        
        # Métriques avancées
        col1, col2, col3, col4 = st.columns(4)
        
        with col1:
            volatility = df['multiplier'].std() / df['multiplier'].mean() * 100
            st.metric("📉 Volatilité", f"{volatility:.1f}%")
        
        with col2:
            median = df['multiplier'].median()
            st.metric("📊 Médiane", f"{median:.2f}x")
        
        with col3:
            q75 = df['multiplier'].quantile(0.75)
            st.metric("📈 Q3 (75%)", f"{q75:.2f}x")
        
        with col4:
            consecutive_low = 0
            for mult in df['multiplier'].iloc[::-1]:
                if mult < 2:
                    consecutive_low += 1
                else:
                    break
            st.metric("🔴 Consécutifs < 2x", consecutive_low)
        
        st.divider()
        
        # Graphiques avancés
        col_a, col_b = st.columns(2)
        
        with col_a:
            st.subheader("📈 Box Plot")
            fig_box = go.Figure(data=[go.Box(
                y=df['multiplier'],
                marker_color='#667eea',
                boxmean='sd'
            )])
            
            fig_box.update_layout(
                yaxis_title="Multiplicateur",
                height=350,
                template="plotly_white",
                showlegend=False
            )
            
            st.plotly_chart(fig_box, use_container_width=True)
        
        with col_b:
            st.subheader("📊 Distribution Cumulative")
            sorted_mult = sorted(df['multiplier'])
            cumulative = [i / len(sorted_mult) * 100 for i in range(1, len(sorted_mult) + 1)]
            
            fig_cum = go.Figure(data=[go.Scatter(
                x=sorted_mult,
                y=cumulative,
                mode='lines',
                fill='tozeroy',
                line=dict(color='#4ECDC4', width=3)
            )])
            
            fig_cum.update_layout(
                xaxis_title="Multiplicateur",
                yaxis_title="% Cumulatif",
                height=350,
                template="plotly_white",
                showlegend=False
            )
            
            st.plotly_chart(fig_cum, use_container_width=True)
    
    with tab3:
        st.subheader(f"📋 Derniers {len(df)} crashs")
        
        # Formater le dataframe
        display_df = df[['timestamp', 'game_id', 'multiplier']].copy()
        display_df['timestamp'] = pd.to_datetime(display_df['timestamp']).dt.strftime('%Y-%m-%d %H:%M:%S')
        display_df = display_df.rename(columns={
            'timestamp': 'Date/Heure',
            'game_id': 'Game ID',
            'multiplier': 'Multiplicateur'
        })
        
        st.dataframe(
            display_df,
            use_container_width=True,
            height=500
        )
        
        # Export CSV
        csv = display_df.to_csv(index=False)
        st.download_button(
            "📥 Télécharger CSV",
            csv,
            "crash_data.csv",
            "text/csv"
        )

except Exception as e:
    st.error(f"❌ Erreur: {e}")
    st.info("Vérifiez que PostgreSQL est accessible et que le scraper tourne")

# Footer
st.divider()
col_f1, col_f2, col_f3, col_f4 = st.columns(4)
with col_f1:
    st.caption(f"🕒 Refresh: {datetime.now().strftime('%H:%M:%S')}")
with col_f2:
    if last_crash and recent_crashes:
        st.caption(f"📦 En mémoire: {len(recent_crashes)} crashs")
    else:
        st.caption("⏳ En attente...")
with col_f3:
    if last_crash:
        seconds_ago = int((datetime.now() - last_crash['timestamp']).total_seconds())
        st.caption(f"⏱️ Dernier: il y a {seconds_ago}s")
    else:
        st.caption("⏱️ Aucun crash")
with col_f4:
    if auto_refresh:
        st.caption(f"🔄 Auto-refresh: {refresh_interval}s")
    else:
        st.caption("⏸️ Auto-refresh OFF")

# Auto-refresh
if auto_refresh:
    time.sleep(refresh_interval)
    st.rerun()
